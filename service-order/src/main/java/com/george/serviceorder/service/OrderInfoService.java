package com.george.serviceorder.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.OrderConstant;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.OrderInfo;
import com.george.internalCommon.dto.PriceRule;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.OrderRequest;
import com.george.internalCommon.request.PriceRuleIsNewRequest;
import com.george.internalCommon.request.PushRequest;
import com.george.internalCommon.response.OrderDriverResponse;
import com.george.internalCommon.response.TerminalResponse;
import com.george.internalCommon.response.TrsearchResponse;
import com.george.internalCommon.util.RedisPrefixUtils;
import com.george.serviceorder.mapper.OrderInfoMapper;
import com.george.serviceorder.remote.ServiceDriverUserClient;
import com.george.serviceorder.remote.ServiceMapClient;
import com.george.serviceorder.remote.ServicePriceClient;
import com.george.serviceorder.remote.ServiceSsePushClient;
import lombok.extern.slf4j.Slf4j;

import org.json.JSONObject;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class is to provide service for controller to deal with orders
 */
@Service
@Slf4j
public class OrderInfoService {

    private static final int MAX_DISPATCH_ATTEMPTS = 6;
    private static final int MAX_FINALIZATION_ATTEMPTS = 3;
    private static final long FINALIZATION_BASE_RETRY_DELAY_SECONDS = 30L;
    private static final long FINALIZATION_PROCESSING_LEASE_SECONDS = 120L;
    private static final int FINALIZATION_RETRY_BATCH_SIZE = 50;
    private long dispatchRetryDelayMs = TimeUnit.SECONDS.toMillis(20);
    private Clock clock = Clock.systemDefaultZone();

    // the mapper to interact with orderInfo database
    @Autowired
    OrderInfoMapper orderInfoMapper;

    // the mapper to interact with price database
    @Autowired
    ServicePriceClient servicePriceClient;

    // the mapper to interact with driver user database
    @Autowired
    ServiceDriverUserClient serviceDriverUserClient;

    // the mapper to interact with redis
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    // the mapper to interact with map service
    @Autowired
    ServiceMapClient serviceMapClient;

    // the mapper to interact with redisson service
    @Autowired
    RedissonClient redissonClient;

    // the mapper to push order to front-end service
    @Autowired
    ServiceSsePushClient serviceSsePushClient;


    /**
     * This function is to create a new order
     * @param orderRequest
     * @return
     */
    public ResponseResult add(OrderRequest orderRequest) {

        // Determine whether the service is provided in current area using the pricing rules
        ResponseResult<Boolean> priceRuleExists = isPriceRuleExists(orderRequest);
        ResponseResult priceRuleExistsFailure = validateRequiredBooleanResponse(priceRuleExists);
        if (priceRuleExistsFailure != null) {
            return priceRuleExistsFailure;
        }
        if(!priceRuleExists.getData()){
            return ResponseResult.fail(CommonStatus.SERVICE_NOT_PROVIDED.getCode(),CommonStatus.SERVICE_NOT_PROVIDED.getMessage());
        }

        // Test if there are currently available drivers in the city
        ResponseResult<Boolean> availableDriver = serviceDriverUserClient.isAvailableDriver(orderRequest.getAddress());
        ResponseResult availableDriverFailure = validateRequiredBooleanResponse(availableDriver);
        if (availableDriverFailure != null) {
            return availableDriverFailure;
        }
        if (!availableDriver.getData()){
            return ResponseResult.fail(CommonStatus.CITY_NO_DRIVER.getCode(),CommonStatus.CITY_NO_DRIVER.getMessage());
        }

        // Check whether the version of the pricing rule is the latest
        PriceRuleIsNewRequest priceRuleIsNewRequest = new PriceRuleIsNewRequest();
        priceRuleIsNewRequest.setFareType(orderRequest.getFareType());
        priceRuleIsNewRequest.setFareVersion(orderRequest.getFareVersion());
        ResponseResult<Boolean> aNew = servicePriceClient.isLatest(priceRuleIsNewRequest);
        ResponseResult latestVersionFailure = validateRequiredBooleanResponse(aNew);
        if (latestVersionFailure != null) {
            return latestVersionFailure;
        }
        if (!(aNew.getData())){
            return ResponseResult.fail(CommonStatus.PRICE_RULE_CHANGED.getCode(),CommonStatus.PRICE_RULE_CHANGED.getMessage());
        }

        // Determine whether the placed device is a blacklist device
        if (isBlackDevice(orderRequest)) {
            return ResponseResult.fail(CommonStatus.DEVICE_IS_BLACK.getCode(), CommonStatus.DEVICE_IS_BLACK.getMessage());
        }


        // Determine if the passenger has an order in progress
        if (isPassengerOrderGoingon(orderRequest.getPassengerId()) > 0){
            return ResponseResult.fail(CommonStatus.ORDER_GOING_ON.getCode(),CommonStatus.ORDER_GOING_ON.getMessage());
        }

        // Create a new order
        OrderInfo orderInfo = new OrderInfo();

        BeanUtils.copyProperties(orderRequest,orderInfo);

        orderInfo.setOrderStatus(OrderConstant.ORDER_START);

        LocalDateTime now = LocalDateTime.now();
        orderInfo.setGmtCreate(now);
        orderInfo.setGmtModified(now);

        orderInfoMapper.insert(orderInfo);

        // Retry real-time dispatch up to 6 times, waiting 20 seconds between failed attempts.
        for (int i =0;i<MAX_DISPATCH_ATTEMPTS;i++){
            // Dispatch real-time order
            int result = dispatchRealTimeOrder(orderInfo);
            if (result == 1){
                break;
            }
            if (i == MAX_DISPATCH_ATTEMPTS - 1){
                // If there is no available driver, the order is invalid
                orderInfo.setOrderStatus(OrderConstant.ORDER_INVALID);
                orderInfoMapper.updateById(orderInfo);
                return ResponseResult.fail(CommonStatus.DISPATCH_FAILED.getCode(),
                        CommonStatus.DISPATCH_FAILED.getMessage());
            }else {
                // wait for 20s
                try {
                    Thread.sleep(dispatchRetryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ResponseResult.fail(CommonStatus.FAIL.getCode(), "Dispatch retry interrupted");
                }
            }



        }

        return ResponseResult.success();
    }


    /**
     * This function is aimed to deal with the real-time order operation
     * @param orderInfo the order needed to be dispatched
     * @return - 1, if successful
     */
    public int dispatchRealTimeOrder(OrderInfo orderInfo){
        int result = 0;

        // the location of the passenger
        String depLatitude = orderInfo.getDepLatitude();
        String depLongitude = orderInfo.getDepLongitude();
        String center = depLatitude+","+depLongitude;

        List<Integer> radiusList = new ArrayList<>();

        // the range to search available drivers from the passenger
        radiusList.add(1000);
        radiusList.add(3000);
        radiusList.add(5000);

        // returning list
        ResponseResult<List<TerminalResponse>> listResponseResult = null;
        radius:
        for (int i=0;i<radiusList.size();i++){
            Integer radius = radiusList.get(i);
            listResponseResult = serviceMapClient.terminalAroundSearch(center,radius );

            if (listResponseResult == null) {
                log.warn("Dispatch terminal search skipped; radius={}, reason=null_response", radius);
                continue;
            }
            if (listResponseResult.getCode() != CommonStatus.SUCCESS.getCode()) {
                log.warn("Dispatch terminal search skipped; radius={}, responseCode={}",
                        radius, listResponseResult.getCode());
                continue;
            }

            // analyze the terminal
            List<TerminalResponse> data = listResponseResult.getData();
            if (data == null) {
                log.warn("Dispatch terminal search skipped; radius={}, reason=null_data", radius);
                continue;
            }
            log.debug("Dispatch terminal search succeeded; radius={}, candidateCount={}", radius, data.size());

            for (int j=0;j<data.size();j++){
                TerminalResponse terminalResponse = data.get(j);
                Long carId = terminalResponse.getCarId();

                String longitude = terminalResponse.getLongitude();
                String latitude = terminalResponse.getLatitude();

                // Check if there are any extra drivers available
                ResponseResult<OrderDriverResponse> availableDriver = serviceDriverUserClient.getAvailableDriver(carId);
                if (availableDriver == null) {
                    log.debug("Dispatch driver candidate skipped; carId={}, reason=null_response", carId);
                    continue;
                }
                if (availableDriver.getCode() != CommonStatus.SUCCESS.getCode()) {
                    log.debug("Dispatch driver candidate skipped; carId={}, responseCode={}",
                            carId, availableDriver.getCode());
                    continue;
                }

                // extract information from that driver
                OrderDriverResponse orderDriverResponse = availableDriver.getData();
                if (orderDriverResponse == null) {
                    log.debug("Dispatch driver candidate skipped; carId={}, reason=null_data", carId);
                    continue;
                }
                else {

                    Long driverId = orderDriverResponse.getDriverId();
                    String driverPhone = orderDriverResponse.getDriverPhone();
                    String licenseId = orderDriverResponse.getLicenseId();
                    String vehicleNo = orderDriverResponse.getVehicleNo();
                    String vehicleTypeFromCar = orderDriverResponse.getVehicleType();

                    // Does the model of the vehicle match?
                    String vehicleType = orderInfo.getVehicleType();
                    if (!vehicleType.trim().equals(vehicleTypeFromCar.trim())) {
                        continue ;
                    }


                    // This lock is to prevent multiple threads from ordering the same driver
                    String lockKey = "driver:assignment:" + driverId;
                    RLock lock = redissonClient.getLock(lockKey);
                    lock.lock();
                    try {

                        // Determine if the driver has an order in progress
                        if (isDriverOrderGoingon(driverId)){
                            continue ;
                        }


                        // Set information about the order and the driver's vehicle
                        orderInfo.setDriverId(driverId);
                        orderInfo.setDriverPhone(driverPhone);
                        orderInfo.setCarId(carId);
                        orderInfo.setReceiveOrderCarLongitude(longitude);
                        orderInfo.setReceiveOrderCarLatitude(latitude);
                        orderInfo.setReceiveOrderTime(LocalDateTime.now());
                        orderInfo.setLicenseId(licenseId);
                        orderInfo.setVehicleNo(vehicleNo);
                        orderInfo.setOrderStatus(OrderConstant.DRIVER_RECEIVE_ORDER);

                        orderInfoMapper.updateById(orderInfo);

                        // Notify the driver
                        JSONObject driverContent = new JSONObject();
                        driverContent.put("orderId",orderInfo.getId());
                        driverContent.put("passengerId",orderInfo.getPassengerId());
                        driverContent.put("passengerPhone",orderInfo.getPassengerPhone());
                        driverContent.put("departure",orderInfo.getDeparture());
                        driverContent.put("depLongitude",orderInfo.getDepLongitude());
                        driverContent.put("depLatitude",orderInfo.getDepLatitude());
                        driverContent.put("destination",orderInfo.getDestination());
                        driverContent.put("destLongitude",orderInfo.getDestLongitude());
                        driverContent.put("destLatitude",orderInfo.getDestLatitude());
                        PushRequest pushRequest = new PushRequest();
                        pushRequest.setUserId(driverId);
                        pushRequest.setIdentity(UserIdentity.DRIVER.getIdentity());
                        pushRequest.setContent(driverContent.toString());
                        serviceSsePushClient.push(pushRequest);

                        // Notify the passenger
                        JSONObject passengerContent = new  JSONObject();
                        passengerContent.put("orderId",orderInfo.getId());
                        passengerContent.put("driverId",orderInfo.getDriverId());
                        passengerContent.put("driverPhone",orderInfo.getDriverPhone());
                        passengerContent.put("vehicleNo",orderInfo.getVehicleNo());

                        // Get vehicle information, calling vehicle service
                        ResponseResult<Car> carById = serviceDriverUserClient.getCarById(carId);
                        Car carRemote = carById.getData();

                        passengerContent.put("brand", carRemote.getBrand());
                        passengerContent.put("model",carRemote.getModel());
                        passengerContent.put("vehicleColor",carRemote.getVehicleColor());

                        passengerContent.put("receiveOrderCarLongitude",orderInfo.getReceiveOrderCarLongitude());
                        passengerContent.put("receiveOrderCarLatitude",orderInfo.getReceiveOrderCarLatitude());

                        PushRequest pushRequest1 = new PushRequest();
                        pushRequest1.setUserId(orderInfo.getPassengerId());
                        pushRequest1.setIdentity(UserIdentity.PASSENGER.getIdentity());
                        pushRequest1.setContent(passengerContent.toString());

                        serviceSsePushClient.push(pushRequest1);
                        result = 1;

                        // Exit, no more driver search, if the order is sent successfully
                        break radius;
                    } finally {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                }

            }

        }

        return  result;
    }


    /**
     * This method is used to determine whether a pricing rule exists
     * @param orderRequest
     * @return
     */
    private ResponseResult<Boolean> isPriceRuleExists(OrderRequest orderRequest){
        String fareType = orderRequest.getFareType();
        int index = fareType.indexOf("$");
        String cityCode = fareType.substring(0, index);
        String vehicleType = fareType.substring(index + 1);

        PriceRule priceRule = new PriceRule();
        priceRule.setCityCode(cityCode);
        priceRule.setVehicleType(vehicleType);

        ResponseResult<Boolean> booleanResponseResult = servicePriceClient.ifPriceExists(priceRule);
        return booleanResponseResult;

    }

    private ResponseResult validateRequiredBooleanResponse(ResponseResult<Boolean> response) {
        if (response == null) {
            return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                    CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
        }
        if (response.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(response.getCode(), response.getMessage());
        }
        if (response.getData() == null) {
            return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                    CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
        }
        return null;
    }


    /**
     * This method is used to determine whether a passenger is on a blacklist
     * @param orderRequest
     * @return
     */
    private boolean isBlackDevice(OrderRequest orderRequest) {
        String deviceCode = orderRequest.getDeviceCode();
        // generate key for black list search
        String deviceCodeKey = RedisPrefixUtils.blackDeviceCodePrefix + deviceCode;
        Boolean aBoolean = stringRedisTemplate.hasKey(deviceCodeKey);
        if (aBoolean){
            String str = stringRedisTemplate.opsForValue().get(deviceCodeKey);
            int i = Integer.parseInt(str);
            // Once the same passenger orders more than twice within an hour, put him on the blacklist
            if (i >= 2){
                return true;
            }else {
                stringRedisTemplate.opsForValue().increment(deviceCodeKey);
            }

        }else {
            stringRedisTemplate.opsForValue().setIfAbsent(deviceCodeKey,"1",1L, TimeUnit.HOURS);
        }
        return false;
    }


    /**
     * This method is used to determine whether the current passenger has an order in progress
     * @param passengerId
     * @return
     */
    private int isPassengerOrderGoingon(Long passengerId){
        // No order is allowed if there is an order in progress
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("passenger_id",passengerId);
        queryWrapper.and(wrapper->wrapper.eq("order_status",OrderConstant.ORDER_START)
                .or().eq("order_status",OrderConstant.DRIVER_RECEIVE_ORDER)
                .or().eq("order_status",OrderConstant.DRIVER_TO_PICK_UP_PASSENGER)
                .or().eq("order_status",OrderConstant.DRIVER_ARRIVED_DEPARTURE)
                .or().eq("order_status",OrderConstant.PICK_UP_PASSENGER)
                .or().eq("order_status",OrderConstant.PASSENGER_GETOFF)
                .or().eq("order_status",OrderConstant.TO_START_PAY)
                .or().eq("order_status",OrderConstant.FINALIZATION_PENDING)
                .or().eq("order_status",OrderConstant.FINALIZATION_FAILED)
        );


        Integer validOrderNumber = orderInfoMapper.selectCount(queryWrapper);

        return validOrderNumber;

    }


    /**
     * This method is used to determine whether the current driver has an order in progress
     * @param driverId
     * @return
     */
    private boolean isDriverOrderGoingon(Long driverId){
        // No order is allowed if there is an order in progress
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("driver_id",driverId);
        queryWrapper.and(wrapper->wrapper
                .eq("order_status",OrderConstant.DRIVER_RECEIVE_ORDER)
                .or().eq("order_status",OrderConstant.DRIVER_TO_PICK_UP_PASSENGER)
                .or().eq("order_status",OrderConstant.DRIVER_ARRIVED_DEPARTURE)
                .or().eq("order_status",OrderConstant.PICK_UP_PASSENGER)

        );


        Integer validOrderNumber = orderInfoMapper.selectCount(queryWrapper);

        return validOrderNumber > 0;

    }


    /**
     * This method is used to handle the state when the driver goes to pick up the passenger
     * @param orderRequest
     * @return
     */
    public ResponseResult toPickUpPassenger(OrderRequest orderRequest){
        Long orderId = orderRequest.getOrderId();
        String toPickUpPassengerLongitude = orderRequest.getToPickUpPassengerLongitude();
        String toPickUpPassengerLatitude = orderRequest.getToPickUpPassengerLatitude();
        String toPickUpPassengerAddress = orderRequest.getToPickUpPassengerAddress();
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id",orderId);
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);

        orderInfo.setToPickUpPassengerAddress(toPickUpPassengerAddress);
        orderInfo.setToPickUpPassengerLatitude(toPickUpPassengerLatitude);
        orderInfo.setToPickUpPassengerLongitude(toPickUpPassengerLongitude);
        orderInfo.setToPickUpPassengerTime(LocalDateTime.now());
        orderInfo.setOrderStatus(OrderConstant.DRIVER_TO_PICK_UP_PASSENGER);

        orderInfoMapper.updateById(orderInfo);

        return ResponseResult.success();

    }


    /**
     * This method is used to deal with the status of the driver when he arrives at the passenger pick-up point
     * @param orderRequest
     * @return
     */
    public ResponseResult arrivedDeparture(OrderRequest orderRequest){
        Long orderId = orderRequest.getOrderId();

        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id",orderId);

        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        orderInfo.setOrderStatus(OrderConstant.DRIVER_ARRIVED_DEPARTURE);

        orderInfo.setDriverArrivedDepartureTime(LocalDateTime.now());
        orderInfoMapper.updateById(orderInfo);
        return ResponseResult.success();
    }


    /**
     * This method is used to deal with the status of the driver when he picks up the passenger
     * @param orderRequest
     * @return
     */
    public ResponseResult pickUpPassenger(@RequestBody OrderRequest orderRequest){
        Long orderId = orderRequest.getOrderId();

        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id",orderId);
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);

        orderInfo.setPickUpPassengerLongitude(orderRequest.getPickUpPassengerLongitude());
        orderInfo.setPickUpPassengerLatitude(orderRequest.getPickUpPassengerLatitude());
        orderInfo.setPickUpPassengerTime(LocalDateTime.now());
        orderInfo.setOrderStatus(OrderConstant.PICK_UP_PASSENGER);

        orderInfoMapper.updateById(orderInfo);
        return ResponseResult.success();
    }

    /**
     * This method is used to deal with the status of the passenger when the trip terminates
     * after the passenger disembarks and reaches the destination
     * @param orderRequest
     * @return
     */
    public ResponseResult passengerGetoff(@RequestBody OrderRequest orderRequest){
        Long orderId = orderRequest.getOrderId();

        OrderInfo orderInfo = selectOrderById(orderId);
        if (orderInfo == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Integer orderStatus = orderInfo.getOrderStatus();
        if (isFinalizationIdempotentSuccess(orderStatus)) {
            return ResponseResult.success();
        }
        if (isOrderStatus(orderStatus, OrderConstant.FINALIZATION_FAILED)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                    CommonStatus.FINALIZATION_FAILED.getMessage());
        }
        if (!isFinalizationEligible(orderStatus)) {
            return ResponseResult.fail(CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getCode(),
                    CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getMessage());
        }
        if (isOrderStatus(orderStatus, OrderConstant.FINALIZATION_PENDING) && !isFinalizationDue(orderInfo, now)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(),
                    CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage());
        }

        return claimAndFinalize(orderInfo, orderRequest, now);
    }

    public ResponseResult retryFinalization(Long orderId) {
        OrderInfo orderInfo = selectOrderById(orderId);
        if (orderInfo == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Integer orderStatus = orderInfo.getOrderStatus();
        if (isFinalizationIdempotentSuccess(orderStatus)) {
            return ResponseResult.success();
        }
        if (isOrderStatus(orderStatus, OrderConstant.FINALIZATION_FAILED)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                    CommonStatus.FINALIZATION_FAILED.getMessage());
        }
        if (!isOrderStatus(orderStatus, OrderConstant.FINALIZATION_PENDING)) {
            return ResponseResult.fail(CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getCode(),
                    CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getMessage());
        }
        if (!isFinalizationDue(orderInfo, now)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(),
                    CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage());
        }

        return claimAndFinalize(orderInfo, null, now);
    }

    public int retryDueFinalizations(LocalDateTime now) {
        return retryDueFinalizations(now, FINALIZATION_RETRY_BATCH_SIZE);
    }

    public int retryDueFinalizations(LocalDateTime now, int batchSize) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_status", OrderConstant.FINALIZATION_PENDING)
                .le("finalization_next_retry_at", now)
                .orderByAsc("finalization_next_retry_at")
                .last("LIMIT " + Math.max(1, batchSize));

        List<OrderInfo> dueOrders = orderInfoMapper.selectList(queryWrapper);
        if (dueOrders == null) {
            return 0;
        }

        int processed = 0;
        for (OrderInfo dueOrder : dueOrders) {
            Long dueOrderId = dueOrder.getId();
            try {
                retryFinalization(dueOrderId);
                processed++;
            } catch (RuntimeException e) {
                log.warn("Order finalization retry failed unexpectedly; orderId={}, exceptionType={}",
                        dueOrderId, e.getClass().getSimpleName());
            }
        }
        return processed;
    }

    private ResponseResult claimAndFinalize(OrderInfo orderInfo, OrderRequest orderRequest, LocalDateTime now) {
        Integer currentStatus = orderInfo.getOrderStatus();
        int currentAttempts = currentFinalizationAttempts(orderInfo);
        if (currentAttempts >= MAX_FINALIZATION_ATTEMPTS) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                    CommonStatus.FINALIZATION_FAILED.getMessage());
        }

        int attempt = currentAttempts + 1;
        LocalDateTime processingLeaseUntil = now.plusSeconds(FINALIZATION_PROCESSING_LEASE_SECONDS);
        Long traceEndEpochMs = orderInfo.getFinalizationTraceEndEpochMs();
        LocalDateTime passengerGetoffTime = orderInfo.getPassengerGetoffTime();
        String passengerGetoffLongitude = orderInfo.getPassengerGetoffLongitude();
        String passengerGetoffLatitude = orderInfo.getPassengerGetoffLatitude();

        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderInfo.getId())
                .eq("order_status", currentStatus)
                .eq("finalization_attempts", currentAttempts);
        if (isOrderStatus(currentStatus, OrderConstant.FINALIZATION_PENDING)) {
            updateWrapper.and(wrapper -> wrapper.isNull("finalization_next_retry_at")
                    .or().le("finalization_next_retry_at", now));
        }
        updateWrapper.set("order_status", OrderConstant.FINALIZATION_PENDING)
                .set("finalization_attempts", attempt)
                .set("finalization_next_retry_at", processingLeaseUntil)
                .set("finalization_last_error", null)
                .set("gmt_modified", now);

        if (isOrderStatus(currentStatus, OrderConstant.PICK_UP_PASSENGER)) {
            Instant traceEnd = Instant.now(clock);
            traceEndEpochMs = traceEnd.toEpochMilli();
            passengerGetoffTime = LocalDateTime.ofInstant(traceEnd, ZoneId.systemDefault());
            passengerGetoffLongitude = orderRequest.getPassengerGetoffLongitude();
            passengerGetoffLatitude = orderRequest.getPassengerGetoffLatitude();
            updateWrapper.set("passenger_getoff_time", passengerGetoffTime)
                    .set("passenger_getoff_longitude", passengerGetoffLongitude)
                    .set("passenger_getoff_latitude", passengerGetoffLatitude)
                    .set("finalization_trace_end_epoch_ms", traceEndEpochMs);
        }

        int claimed = orderInfoMapper.update(null, updateWrapper);
        if (claimed == 0) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(),
                    CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage());
        }

        orderInfo.setOrderStatus(OrderConstant.FINALIZATION_PENDING);
        orderInfo.setFinalizationAttempts(attempt);
        orderInfo.setFinalizationNextRetryAt(processingLeaseUntil);
        orderInfo.setFinalizationLastError(null);
        orderInfo.setGmtModified(now);
        orderInfo.setPassengerGetoffTime(passengerGetoffTime);
        orderInfo.setPassengerGetoffLongitude(passengerGetoffLongitude);
        orderInfo.setPassengerGetoffLatitude(passengerGetoffLatitude);
        orderInfo.setFinalizationTraceEndEpochMs(traceEndEpochMs);

        return finalizeClaimedOrder(orderInfo, attempt, now);
    }

    private ResponseResult finalizeClaimedOrder(OrderInfo orderInfo, int attempt, LocalDateTime now) {
        ResponseResult<Car> carById = null;
        if (orderInfo.getCarId() != null) {
            carById = serviceDriverUserClient.getCarById(orderInfo.getCarId());
        }
        ResponseResult carFailure = validateCarLookup(carById);
        if (carFailure != null) {
            return handleFinalizationFailure(orderInfo, attempt, carFailure, now);
        }

        Car car = carById.getData();
        ResponseResult tracePrerequisiteFailure = validateTracePrerequisites(orderInfo, car);
        if (tracePrerequisiteFailure != null) {
            return handleFinalizationFailure(orderInfo, attempt, tracePrerequisiteFailure, now);
        }

        Long starttime = orderInfo.getPickUpPassengerTime()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        Long endtime = orderInfo.getFinalizationTraceEndEpochMs();

        ResponseResult<TrsearchResponse> trsearch = serviceMapClient.trsearch(car.getTid(), starttime,endtime);
        ResponseResult trackFailure = validateTrackLookup(trsearch);
        if (trackFailure != null) {
            return handleFinalizationFailure(orderInfo, attempt, trackFailure, now);
        }
        TrsearchResponse data = trsearch.getData();
        Long driveMile = data.getDriveMile();
        Long driveDurationSeconds = data.getDriveTime();

        // get the actual price
        String address = orderInfo.getAddress();
        String vehicleType = orderInfo.getVehicleType();
        ResponseResult<Double> doubleResponseResult = servicePriceClient.calculatePrice(driveMile.intValue(), driveDurationSeconds.intValue(), address, vehicleType);
        ResponseResult priceFailure = validatePriceLookup(doubleResponseResult);
        if (priceFailure != null) {
            return handleFinalizationFailure(orderInfo, attempt, priceFailure, now);
        }
        Double price = doubleResponseResult.getData();

        orderInfo.setDriveMile(driveMile);
        orderInfo.setDriveTime(driveDurationSeconds);
        orderInfo.setPrice(price);
        orderInfo.setOrderStatus(OrderConstant.PASSENGER_GETOFF);
        orderInfo.setFinalizationNextRetryAt(null);
        orderInfo.setFinalizationLastError(null);
        orderInfo.setGmtModified(now);

        orderInfoMapper.updateById(orderInfo);
        return ResponseResult.success();
    }

    private ResponseResult handleFinalizationFailure(OrderInfo orderInfo, int attempt, ResponseResult failure, LocalDateTime now) {
        orderInfo.setFinalizationLastError(safeFinalizationError(failure));
        orderInfo.setGmtModified(now);

        if (attempt >= MAX_FINALIZATION_ATTEMPTS) {
            orderInfo.setOrderStatus(OrderConstant.FINALIZATION_FAILED);
            orderInfo.setFinalizationNextRetryAt(null);
            orderInfoMapper.updateById(orderInfo);
            log.warn("Order finalization reached retry limit; orderId={}, attempt={}, responseCode={}",
                    orderInfo.getId(), attempt, failure.getCode());
            return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                    CommonStatus.FINALIZATION_FAILED.getMessage());
        }

        LocalDateTime nextRetryAt = now.plusSeconds(finalizationBackoffSeconds(attempt));
        orderInfo.setOrderStatus(OrderConstant.FINALIZATION_PENDING);
        orderInfo.setFinalizationNextRetryAt(nextRetryAt);
        orderInfoMapper.updateById(orderInfo);
        log.warn("Order finalization scheduled for retry; orderId={}, attempt={}, responseCode={}, nextRetryAt={}",
                orderInfo.getId(), attempt, failure.getCode(), nextRetryAt);
        return ResponseResult.fail(failure.getCode(), failure.getMessage());
    }

    private OrderInfo selectOrderById(Long orderId) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id",orderId);
        return orderInfoMapper.selectOne(queryWrapper);
    }

    private boolean isFinalizationIdempotentSuccess(Integer orderStatus) {
        return isOrderStatus(orderStatus, OrderConstant.PASSENGER_GETOFF)
                || isOrderStatus(orderStatus, OrderConstant.TO_START_PAY)
                || isOrderStatus(orderStatus, OrderConstant.SUCCESS_PAY);
    }

    private boolean isFinalizationEligible(Integer orderStatus) {
        return isOrderStatus(orderStatus, OrderConstant.PICK_UP_PASSENGER)
                || isOrderStatus(orderStatus, OrderConstant.FINALIZATION_PENDING);
    }

    private boolean isOrderStatus(Integer actualStatus, int expectedStatus) {
        return actualStatus != null && actualStatus == expectedStatus;
    }

    private boolean isFinalizationDue(OrderInfo orderInfo, LocalDateTime now) {
        LocalDateTime nextRetryAt = orderInfo.getFinalizationNextRetryAt();
        return nextRetryAt == null || !nextRetryAt.isAfter(now);
    }

    private int currentFinalizationAttempts(OrderInfo orderInfo) {
        Integer finalizationAttempts = orderInfo.getFinalizationAttempts();
        if (finalizationAttempts == null) {
            return 0;
        }
        return finalizationAttempts;
    }

    private long finalizationBackoffSeconds(int attempt) {
        if (attempt <= 1) {
            return FINALIZATION_BASE_RETRY_DELAY_SECONDS;
        }
        return FINALIZATION_BASE_RETRY_DELAY_SECONDS * (1L << (attempt - 1));
    }

    private ResponseResult validateCarLookup(ResponseResult<Car> carById) {
        if (carById == null) {
            return downstreamResponseError();
        }
        if (carById.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(carById.getCode(), carById.getMessage());
        }
        Car car = carById.getData();
        if (car == null || car.getTid() == null || car.getTid().trim().isEmpty()) {
            return downstreamResponseError();
        }
        return null;
    }

    private ResponseResult validateTracePrerequisites(OrderInfo orderInfo, Car car) {
        if (car == null || car.getTid() == null || car.getTid().trim().isEmpty()) {
            return downstreamResponseError();
        }
        if (orderInfo.getPickUpPassengerTime() == null || orderInfo.getFinalizationTraceEndEpochMs() == null) {
            return downstreamResponseError();
        }
        return null;
    }

    private ResponseResult validateTrackLookup(ResponseResult<TrsearchResponse> trsearch) {
        if (trsearch == null) {
            return downstreamResponseError();
        }
        if (trsearch.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(trsearch.getCode(), trsearch.getMessage());
        }
        TrsearchResponse data = trsearch.getData();
        if (data == null) {
            return downstreamResponseError();
        }
        if (data.getDriveMile() == null || data.getDriveTime() == null) {
            return downstreamResponseError();
        }
        return null;
    }

    private ResponseResult validatePriceLookup(ResponseResult<Double> priceResponse) {
        if (priceResponse == null) {
            return downstreamResponseError();
        }
        if (priceResponse.getCode() != CommonStatus.SUCCESS.getCode()) {
            return ResponseResult.fail(priceResponse.getCode(), priceResponse.getMessage());
        }
        Double price = priceResponse.getData();
        if (price == null || price.isNaN() || price.isInfinite()) {
            return downstreamResponseError();
        }
        return null;
    }

    private ResponseResult downstreamResponseError() {
        return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
    }

    private String safeFinalizationError(ResponseResult failure) {
        int code = failure.getCode();
        String message = CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage();
        for (CommonStatus status : CommonStatus.values()) {
            if (status.getCode() == code) {
                message = status.getMessage();
                break;
            }
        }
        String error = code + ":" + message;
        if (error.length() > 255) {
            return error.substring(0, 255);
        }
        return error;
    }

    /**
     * This method is used to process the status of a passenger when they pay for an order
     * @param orderRequest
     * @return
     */
    public ResponseResult pay(OrderRequest orderRequest){

        Long orderId = orderRequest.getOrderId();
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);

        orderInfo.setOrderStatus(OrderConstant.SUCCESS_PAY);
        orderInfoMapper.updateById(orderInfo);
        return ResponseResult.success();
    }

    /**
     * This method is used to handle the status of an order when it is canceled
     * @param orderId
     * @param identity  Identity: 1: passenger, 2: driver
     * @return
     */
    public ResponseResult cancel(Long orderId, String identity){
        // Query the current status of the order
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        Integer orderStatus = orderInfo.getOrderStatus();

        LocalDateTime cancelTime = LocalDateTime.now();
        Integer cancelOperator = null;
        Integer cancelTypeCode = null;

        // Normal cancellation
        int cancelType = 1;

        // Update the cancellation status of the order
        // If a passenger cancels
        if (identity.trim().equals(UserIdentity.PASSENGER.getIdentity())){
            switch (orderStatus){
                // At the start of the order
                case OrderConstant.ORDER_START:
                    cancelTypeCode = OrderConstant.CANCEL_PASSENGER_BEFORE;
                    break;
                // When the driver receives the order
                case OrderConstant.DRIVER_RECEIVE_ORDER:
                    LocalDateTime receiveOrderTime = orderInfo.getReceiveOrderTime();
                    long between = ChronoUnit.MINUTES.between(receiveOrderTime, cancelTime);
                    if (between > 1){
                        cancelTypeCode = OrderConstant.CANCEL_PASSENGER_ILLEGAL;
                    }else {
                        cancelTypeCode = OrderConstant.CANCEL_PASSENGER_BEFORE;
                    }
                    break;
                // When the driver goes to pick up the passenger
                case OrderConstant.DRIVER_TO_PICK_UP_PASSENGER:
                // When the driver arrives at the passenger starting point
                case OrderConstant.DRIVER_ARRIVED_DEPARTURE:
                    cancelTypeCode = OrderConstant.CANCEL_PASSENGER_ILLEGAL;
                    break;
                default:
                    cancelType = 0;
                    break;
            }
        }

        // If the driver cancels the order
        if (identity.trim().equals(UserIdentity.DRIVER.getIdentity())){
            switch (orderStatus){
                // At the start of the order
                // When the driver picks up the passenger
                case OrderConstant.DRIVER_RECEIVE_ORDER:
                case OrderConstant.DRIVER_TO_PICK_UP_PASSENGER:
                case OrderConstant.DRIVER_ARRIVED_DEPARTURE:
                    LocalDateTime receiveOrderTime = orderInfo.getReceiveOrderTime();
                    long between = ChronoUnit.MINUTES.between(receiveOrderTime, cancelTime);
                    if (between > 1){
                        cancelTypeCode = OrderConstant.CANCEL_DRIVER_ILLEGAL;
                    }else {
                        cancelTypeCode = OrderConstant.CANCEL_DRIVER_BEFORE;
                    }
                    break;

                default:
                    cancelType = 0;
                    break;
            }
        }

        // 0 means cancel unsuccessfully
        if (cancelType == 0){
            return ResponseResult.fail(CommonStatus.ORDER_CANCEL_ERROR.getCode(), CommonStatus.ORDER_CANCEL_ERROR.getMessage());
        }

        orderInfo.setCancelTypeCode(cancelTypeCode);
        orderInfo.setCancelTime(cancelTime);
        orderInfo.setCancelOperator(Integer.parseInt(identity));
        orderInfo.setOrderStatus(OrderConstant.ORDER_CANCEL);

        orderInfoMapper.updateById(orderInfo);
        return ResponseResult.success();
    }

    /**
     * This method is used to handle the situation when the passenger starts paying for the order
     * @param orderRequest
     * @return
     */
    public ResponseResult pushPayInfo(OrderRequest orderRequest) {

        Long orderId = orderRequest.getOrderId();

        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        orderInfo.setOrderStatus(OrderConstant.TO_START_PAY);
        orderInfoMapper.updateById(orderInfo);
        return ResponseResult.success();

    }


    /**
     * This method is used to get information about the order being made by the current user
     * @param phone
     * @param identity
     * @return
     */
    public ResponseResult<OrderInfo> current(String phone, String identity){
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();

        if (identity.equals(UserIdentity.DRIVER.getIdentity())){
            queryWrapper.eq("driver_phone",phone);

            queryWrapper.and(wrapper->wrapper
                    .eq("order_status",OrderConstant.DRIVER_RECEIVE_ORDER)
                    .or().eq("order_status",OrderConstant.DRIVER_TO_PICK_UP_PASSENGER)
                    .or().eq("order_status",OrderConstant.DRIVER_ARRIVED_DEPARTURE)
                    .or().eq("order_status",OrderConstant.PICK_UP_PASSENGER)

            );
        }
        if (identity.equals(UserIdentity.PASSENGER.getIdentity())){
            queryWrapper.eq("passenger_phone",phone);
            queryWrapper.and(wrapper->wrapper.eq("order_status",OrderConstant.ORDER_START)
                    .or().eq("order_status",OrderConstant.DRIVER_RECEIVE_ORDER)
                    .or().eq("order_status",OrderConstant.DRIVER_TO_PICK_UP_PASSENGER)
                    .or().eq("order_status",OrderConstant.DRIVER_ARRIVED_DEPARTURE)
                    .or().eq("order_status",OrderConstant.PICK_UP_PASSENGER)
                    .or().eq("order_status",OrderConstant.PASSENGER_GETOFF)
                    .or().eq("order_status",OrderConstant.TO_START_PAY)
                    .or().eq("order_status",OrderConstant.FINALIZATION_PENDING)
                    .or().eq("order_status",OrderConstant.FINALIZATION_FAILED)
            );
        }

        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        return ResponseResult.success(orderInfo);
    }
}
