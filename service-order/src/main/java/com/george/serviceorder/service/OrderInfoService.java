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
import com.george.serviceorder.remote.FinalizationDriverUserClient;
import com.george.serviceorder.remote.FinalizationMapClient;
import com.george.serviceorder.remote.FinalizationPriceClient;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import javax.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This class is to provide service for controller to deal with orders
 */
@Service
@Slf4j
public class OrderInfoService {

    private static final int MAX_DISPATCH_ATTEMPTS = 6;
    private static final int FINALIZATION_RETRY_BATCH_SIZE = 50;
    private static final String PRESERVE_GMT_CREATE_SQL = "gmt_create = gmt_create";
    private static final String PRESERVE_GMT_MODIFIED_SQL = "gmt_modified = gmt_modified";
    private long dispatchRetryDelayMs = TimeUnit.SECONDS.toMillis(20);
    private Clock clock = Clock.systemDefaultZone();

    @Value("${order.finalization.max-attempts:3}")
    private int maxFinalizationAttempts = 3;

    @Value("${order.finalization.base-retry-delay-seconds:30}")
    private long finalizationBaseRetryDelaySeconds = 30L;

    @Value("${order.finalization.max-retry-delay-seconds:900}")
    private long finalizationMaxRetryDelaySeconds = 900L;

    @Value("${order.finalization.processing-lease-seconds:120}")
    private long finalizationProcessingLeaseSeconds = 120L;

    @Value("${order.finalization.lease-safety-margin-ms:30000}")
    private long finalizationLeaseSafetyMarginMs = 30000L;

    @Value("${feign.client.config.finalizationDriverUserClient.connectTimeout:2000}")
    private int finalizationDriverConnectTimeoutMs = 2000;

    @Value("${feign.client.config.finalizationDriverUserClient.readTimeout:10000}")
    private int finalizationDriverReadTimeoutMs = 10000;

    @Value("${feign.client.config.finalizationMapClient.connectTimeout:2000}")
    private int finalizationMapConnectTimeoutMs = 2000;

    @Value("${feign.client.config.finalizationMapClient.readTimeout:30000}")
    private int finalizationMapReadTimeoutMs = 30000;

    @Value("${feign.client.config.finalizationPriceClient.connectTimeout:2000}")
    private int finalizationPriceConnectTimeoutMs = 2000;

    @Value("${feign.client.config.finalizationPriceClient.readTimeout:10000}")
    private int finalizationPriceReadTimeoutMs = 10000;

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

    @Autowired
    FinalizationPriceClient finalizationPriceClient;

    @Autowired
    FinalizationDriverUserClient finalizationDriverUserClient;

    @Autowired
    FinalizationMapClient finalizationMapClient;

    // the mapper to interact with redisson service
    @Autowired
    RedissonClient redissonClient;

    // the mapper to push order to front-end service
    @Autowired
    ServiceSsePushClient serviceSsePushClient;

    @PostConstruct
    public void validateFinalizationPolicyOnStartup() {
        validateFinalizationPolicy(
                maxFinalizationAttempts,
                finalizationBaseRetryDelaySeconds,
                finalizationMaxRetryDelaySeconds,
                finalizationProcessingLeaseSeconds,
                finalizationLeaseSafetyMarginMs,
                finalizationDriverConnectTimeoutMs,
                finalizationDriverReadTimeoutMs,
                finalizationMapConnectTimeoutMs,
                finalizationMapReadTimeoutMs,
                finalizationPriceConnectTimeoutMs,
                finalizationPriceReadTimeoutMs);
    }


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
        return transitionLegacyOrderState(
                orderId,
                OrderConstant.DRIVER_RECEIVE_ORDER,
                OrderConstant.DRIVER_TO_PICK_UP_PASSENGER,
                updateWrapper -> updateWrapper
                        .set("to_pick_up_passenger_address", toPickUpPassengerAddress)
                        .set("to_pick_up_passenger_latitude", toPickUpPassengerLatitude)
                        .set("to_pick_up_passenger_longitude", toPickUpPassengerLongitude)
                        .set("to_pick_up_passenger_time", LocalDateTime.now(clock)));

    }


    /**
     * This method is used to deal with the status of the driver when he arrives at the passenger pick-up point
     * @param orderRequest
     * @return
     */
    public ResponseResult arrivedDeparture(OrderRequest orderRequest){
        Long orderId = orderRequest.getOrderId();
        return transitionLegacyOrderState(
                orderId,
                OrderConstant.DRIVER_TO_PICK_UP_PASSENGER,
                OrderConstant.DRIVER_ARRIVED_DEPARTURE,
                updateWrapper -> updateWrapper
                        .set("driver_arrived_departure_time", LocalDateTime.now(clock)));
    }


    /**
     * This method is used to deal with the status of the driver when he picks up the passenger
     * @param orderRequest
     * @return
     */
    public ResponseResult pickUpPassenger(@RequestBody OrderRequest orderRequest){
        Long orderId = orderRequest.getOrderId();
        return transitionLegacyOrderState(
                orderId,
                OrderConstant.DRIVER_ARRIVED_DEPARTURE,
                OrderConstant.PICK_UP_PASSENGER,
                updateWrapper -> updateWrapper
                        .set("pick_up_passenger_longitude", orderRequest.getPickUpPassengerLongitude())
                        .set("pick_up_passenger_latitude", orderRequest.getPickUpPassengerLatitude())
                        .set("pick_up_passenger_time", LocalDateTime.now(clock)));
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

    public ResponseResult scheduleFailedFinalizationRecovery(Long orderId) {
        OrderInfo orderInfo = selectOrderById(orderId);
        if (orderInfo == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }

        Integer orderStatus = orderInfo.getOrderStatus();
        if (isFinalizationIdempotentSuccess(orderStatus)) {
            return ResponseResult.success();
        }
        if (isOrderStatus(orderStatus, OrderConstant.FINALIZATION_PENDING)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(),
                    CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage());
        }
        if (!isOrderStatus(orderStatus, OrderConstant.FINALIZATION_FAILED)) {
            return ResponseResult.fail(CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getCode(),
                    CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getMessage());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Integer currentAttempts = orderInfo.getFinalizationAttempts();
        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderId)
                .eq("order_status", OrderConstant.FINALIZATION_FAILED)
                .eq("finalization_attempts", currentAttempts);
        updateWrapper.set("order_status", OrderConstant.FINALIZATION_PENDING)
                .set("finalization_attempts", 0)
                .set("finalization_next_retry_at", now)
                .set("finalization_last_error", safeFinalizationError(ResponseResult.fail(
                        CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getCode(),
                        CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getMessage())))
                .set("gmt_modified", now);
        preserveOrderCreationTime(updateWrapper);

        int updated = orderInfoMapper.update(null, updateWrapper);
        if (updated == 0) {
            return handleFailedFinalizationRecoveryCasMiss(orderId);
        }
        return ResponseResult.fail(CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getCode(),
                CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getMessage());
    }

    public int retryDueFinalizations(LocalDateTime now) {
        return retryDueFinalizations(now, FINALIZATION_RETRY_BATCH_SIZE);
    }

    public int retryDueFinalizations(LocalDateTime now, int batchSize) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_status", OrderConstant.FINALIZATION_PENDING)
                .and(wrapper -> wrapper.isNull("finalization_next_retry_at")
                        .or().le("finalization_next_retry_at", now))
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
        if (isOrderStatus(currentStatus, OrderConstant.FINALIZATION_PENDING)
                && currentAttempts >= maxFinalizationAttempts
                && isFinalizationDue(orderInfo, now)) {
            return moveExpiredFinalizationToFailed(orderInfo, currentAttempts, now);
        }
        if (currentAttempts >= maxFinalizationAttempts) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                    CommonStatus.FINALIZATION_FAILED.getMessage());
        }

        int attempt = currentAttempts + 1;
        LocalDateTime processingLeaseUntil = now.plusSeconds(finalizationProcessingLeaseSeconds);
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
        preserveOrderCreationTime(updateWrapper);

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

        return finalizeClaimedOrder(orderInfo, attempt);
    }

    private ResponseResult finalizeClaimedOrder(OrderInfo orderInfo, int attempt) {
        ResponseResult<Car> carById = null;
        if (orderInfo.getCarId() != null) {
            try {
                carById = finalizationDriverUserClient.getCarById(orderInfo.getCarId());
            } catch (RuntimeException e) {
                logFinalizationDependencyException(orderInfo.getId(), attempt, "getCarById", e);
                return handleFinalizationFailure(orderInfo, attempt, downstreamResponseError());
            }
        }
        ResponseResult carFailure = validateCarLookup(carById);
        if (carFailure != null) {
            return handleFinalizationFailure(orderInfo, attempt, carFailure);
        }

        Car car = carById.getData();
        ResponseResult tracePrerequisiteFailure = validateTracePrerequisites(orderInfo, car);
        if (tracePrerequisiteFailure != null) {
            return handleFinalizationFailure(orderInfo, attempt, tracePrerequisiteFailure);
        }

        Long starttime = orderInfo.getPickUpPassengerTime()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        Long endtime = orderInfo.getFinalizationTraceEndEpochMs();

        ResponseResult<TrsearchResponse> trsearch;
        try {
            trsearch = finalizationMapClient.trsearch(car.getTid(), starttime,endtime);
        } catch (RuntimeException e) {
            logFinalizationDependencyException(orderInfo.getId(), attempt, "trsearch", e);
            return handleFinalizationFailure(orderInfo, attempt, downstreamResponseError());
        }
        ResponseResult trackFailure = validateTrackLookup(trsearch);
        if (trackFailure != null) {
            return handleFinalizationFailure(orderInfo, attempt, trackFailure);
        }
        TrsearchResponse data = trsearch.getData();
        Long driveMile = data.getDriveMile();
        Long driveDurationSeconds = data.getDriveTime();
        int pricingDistance = Math.toIntExact(driveMile);
        int pricingDuration = Math.toIntExact(driveDurationSeconds);

        // get the actual price
        String address = orderInfo.getAddress();
        String vehicleType = orderInfo.getVehicleType();
        ResponseResult<Double> doubleResponseResult;
        try {
            doubleResponseResult = finalizationPriceClient.calculatePrice(pricingDistance, pricingDuration, address, vehicleType);
        } catch (RuntimeException e) {
            logFinalizationDependencyException(orderInfo.getId(), attempt, "calculatePrice", e);
            return handleFinalizationFailure(orderInfo, attempt, downstreamResponseError());
        }
        ResponseResult priceFailure = validatePriceLookup(doubleResponseResult);
        if (priceFailure != null) {
            return handleFinalizationFailure(orderInfo, attempt, priceFailure);
        }
        Double price = doubleResponseResult.getData();
        LocalDateTime completionTime = LocalDateTime.now(clock);

        UpdateWrapper<OrderInfo> updateWrapper = finalizationTerminalUpdateWrapper(orderInfo, attempt);
        updateWrapper.set("order_status", OrderConstant.PASSENGER_GETOFF)
                .set("drive_mile", driveMile)
                .set("drive_time", driveDurationSeconds)
                .set("price", price)
                .set("finalization_next_retry_at", null)
                .set("finalization_last_error", null)
                .set("gmt_modified", completionTime);

        int updated = orderInfoMapper.update(null, updateWrapper);
        if (updated == 0) {
            return handleFinalizationTerminalCasMiss(orderInfo.getId());
        }
        return ResponseResult.success();
    }

    private ResponseResult handleFinalizationFailure(OrderInfo orderInfo, int attempt, ResponseResult failure) {
        LocalDateTime failureTime = LocalDateTime.now(clock);
        ResponseResult canonicalFailure = canonicalFinalizationFailure(failure);
        String safeError = safeFinalizationError(canonicalFailure);

        if (attempt >= maxFinalizationAttempts) {
            UpdateWrapper<OrderInfo> updateWrapper = finalizationTerminalUpdateWrapper(orderInfo, attempt);
            updateWrapper.set("order_status", OrderConstant.FINALIZATION_FAILED)
                    .set("finalization_next_retry_at", null)
                    .set("finalization_last_error", safeError)
                    .set("gmt_modified", failureTime);
            int updated = orderInfoMapper.update(null, updateWrapper);
            if (updated == 0) {
                return handleFinalizationTerminalCasMiss(orderInfo.getId());
            }
            log.warn("Order finalization reached retry limit; orderId={}, attempt={}, responseCode={}",
                    orderInfo.getId(), attempt, canonicalFailure.getCode());
            return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                    CommonStatus.FINALIZATION_FAILED.getMessage());
        }

        LocalDateTime nextRetryAt = failureTime.plusSeconds(finalizationBackoffSeconds(attempt));
        UpdateWrapper<OrderInfo> updateWrapper = finalizationTerminalUpdateWrapper(orderInfo, attempt);
        updateWrapper.set("order_status", OrderConstant.FINALIZATION_PENDING)
                .set("finalization_next_retry_at", nextRetryAt)
                .set("finalization_last_error", safeError)
                .set("gmt_modified", failureTime);
        int updated = orderInfoMapper.update(null, updateWrapper);
        if (updated == 0) {
            return handleFinalizationTerminalCasMiss(orderInfo.getId());
        }
        log.warn("Order finalization scheduled for retry; orderId={}, attempt={}, responseCode={}, nextRetryAt={}",
                orderInfo.getId(), attempt, canonicalFailure.getCode(), nextRetryAt);
        return ResponseResult.fail(canonicalFailure.getCode(), canonicalFailure.getMessage());
    }

    private ResponseResult moveExpiredFinalizationToFailed(OrderInfo orderInfo, int currentAttempts, LocalDateTime now) {
        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderInfo.getId())
                .eq("order_status", OrderConstant.FINALIZATION_PENDING)
                .eq("finalization_attempts", currentAttempts)
                .and(wrapper -> wrapper.isNull("finalization_next_retry_at")
                        .or().le("finalization_next_retry_at", now));
        updateWrapper.set("order_status", OrderConstant.FINALIZATION_FAILED)
                .set("finalization_next_retry_at", null)
                .set("finalization_last_error", safeFinalizationError(ResponseResult.fail(
                        CommonStatus.FINALIZATION_FAILED.getCode(),
                        CommonStatus.FINALIZATION_FAILED.getMessage())))
                .set("gmt_modified", now);
        preserveOrderCreationTime(updateWrapper);

        int updated = orderInfoMapper.update(null, updateWrapper);
        if (updated == 0) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(),
                    CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage());
        }
        return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                CommonStatus.FINALIZATION_FAILED.getMessage());
    }

    private UpdateWrapper<OrderInfo> finalizationTerminalUpdateWrapper(OrderInfo orderInfo, int attempt) {
        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderInfo.getId())
                .eq("order_status", OrderConstant.FINALIZATION_PENDING)
                .eq("finalization_attempts", attempt);
        preserveOrderCreationTime(updateWrapper);
        return updateWrapper;
    }

    private ResponseResult handleFinalizationTerminalCasMiss(Long orderId) {
        OrderInfo latest = selectOrderById(orderId);
        if (latest != null) {
            Integer latestStatus = latest.getOrderStatus();
            if (isFinalizationIdempotentSuccess(latestStatus)) {
                return ResponseResult.success();
            }
            if (isOrderStatus(latestStatus, OrderConstant.FINALIZATION_FAILED)) {
                return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                        CommonStatus.FINALIZATION_FAILED.getMessage());
            }
        }
        return ResponseResult.fail(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(),
                CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage());
    }

    private ResponseResult handleFailedFinalizationRecoveryCasMiss(Long orderId) {
        OrderInfo latest = selectOrderById(orderId);
        if (latest == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        Integer latestStatus = latest.getOrderStatus();
        if (isFinalizationIdempotentSuccess(latestStatus)) {
            return ResponseResult.success();
        }
        if (isOrderStatus(latestStatus, OrderConstant.FINALIZATION_PENDING)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getCode(),
                    CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getMessage());
        }
        if (isOrderStatus(latestStatus, OrderConstant.FINALIZATION_FAILED)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_FAILED.getCode(),
                    CommonStatus.FINALIZATION_FAILED.getMessage());
        }
        return ResponseResult.fail(CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getCode(),
                CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getMessage());
    }

    private void logFinalizationDependencyException(Long orderId, int attempt, String dependencyName, RuntimeException e) {
        log.warn("Order finalization dependency failed; orderId={}, attempt={}, dependency={}, exceptionType={}",
                orderId, attempt, dependencyName, e.getClass().getSimpleName());
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

    private boolean isFinalizationOwnedStatus(Integer orderStatus) {
        return isOrderStatus(orderStatus, OrderConstant.FINALIZATION_PENDING)
                || isOrderStatus(orderStatus, OrderConstant.FINALIZATION_FAILED);
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
        long delay = finalizationBaseRetryDelaySeconds;
        if (attempt <= 1) {
            return Math.min(delay, finalizationMaxRetryDelaySeconds);
        }
        for (int i = 1; i < attempt; i++) {
            if (delay >= finalizationMaxRetryDelaySeconds
                    || delay > finalizationMaxRetryDelaySeconds / 2) {
                return finalizationMaxRetryDelaySeconds;
            }
            delay = delay * 2;
        }
        return Math.min(delay, finalizationMaxRetryDelaySeconds);
    }

    void validateFinalizationPolicy(
            int maxAttempts,
            long baseRetryDelaySeconds,
            long maxRetryDelaySeconds,
            long processingLeaseSeconds,
            long leaseSafetyMarginMs,
            int driverConnectTimeoutMs,
            int driverReadTimeoutMs,
            int mapConnectTimeoutMs,
            int mapReadTimeoutMs,
            int priceConnectTimeoutMs,
            int priceReadTimeoutMs) {

        if (maxAttempts < 1 || maxAttempts > 100) {
            throw invalidFinalizationPolicy();
        }
        if (baseRetryDelaySeconds <= 0
                || maxRetryDelaySeconds <= 0
                || processingLeaseSeconds <= 0
                || leaseSafetyMarginMs <= 0) {
            throw invalidFinalizationPolicy();
        }
        if (baseRetryDelaySeconds > maxRetryDelaySeconds) {
            throw invalidFinalizationPolicy();
        }
        if (driverConnectTimeoutMs <= 0
                || driverReadTimeoutMs <= 0
                || mapConnectTimeoutMs <= 0
                || mapReadTimeoutMs <= 0
                || priceConnectTimeoutMs <= 0
                || priceReadTimeoutMs <= 0) {
            throw invalidFinalizationPolicy();
        }

        long processingLeaseMillis = secondsToMillis(processingLeaseSeconds);
        long remoteBudgetMillis = checkedAdd(
                checkedAdd(
                        checkedAdd(driverConnectTimeoutMs, driverReadTimeoutMs),
                        checkedAdd(mapConnectTimeoutMs, mapReadTimeoutMs)),
                checkedAdd(priceConnectTimeoutMs, priceReadTimeoutMs));
        long requiredLeaseMillis = checkedAdd(remoteBudgetMillis, leaseSafetyMarginMs);
        if (processingLeaseMillis <= requiredLeaseMillis) {
            throw invalidFinalizationPolicy();
        }
    }

    private long secondsToMillis(long seconds) {
        try {
            return Math.multiplyExact(seconds, TimeUnit.SECONDS.toMillis(1));
        } catch (ArithmeticException e) {
            throw invalidFinalizationPolicy();
        }
    }

    private long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException e) {
            throw invalidFinalizationPolicy();
        }
    }

    private IllegalStateException invalidFinalizationPolicy() {
        return new IllegalStateException("Invalid order finalization policy configuration");
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
        Long driveMile = data.getDriveMile();
        Long driveTime = data.getDriveTime();
        if (driveMile < 0 || driveTime < 0) {
            return downstreamResponseError();
        }
        if (driveMile > Integer.MAX_VALUE || driveTime > Integer.MAX_VALUE) {
            return downstreamResponseError();
        }
        if (driveMile == 0L && driveTime == 0L) {
            return ResponseResult.fail(CommonStatus.MAP_TRACK_EMPTY.getCode(),
                    CommonStatus.MAP_TRACK_EMPTY.getMessage());
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
        if (price == null || price.isNaN() || price.isInfinite() || price < 0) {
            return downstreamResponseError();
        }
        return null;
    }

    private ResponseResult downstreamResponseError() {
        return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
    }

    private ResponseResult canonicalFinalizationFailure(ResponseResult failure) {
        if (failure == null) {
            return downstreamResponseError();
        }
        CommonStatus knownStatus = findCommonStatus(failure.getCode());
        if (knownStatus == null) {
            return downstreamResponseError();
        }
        return ResponseResult.fail(knownStatus.getCode(), knownStatus.getMessage());
    }

    private String safeFinalizationError(ResponseResult failure) {
        int code = failure.getCode();
        String message = CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage();
        CommonStatus knownStatus = findCommonStatus(code);
        if (knownStatus != null) {
            message = knownStatus.getMessage();
        }
        String error = code + ":" + message;
        if (error.length() > 255) {
            return error.substring(0, 255);
        }
        return error;
    }

    private CommonStatus findCommonStatus(int code) {
        for (CommonStatus status : CommonStatus.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }

    private ResponseResult transitionLegacyOrderState(
            Long orderId,
            int expectedStatus,
            int targetStatus,
            Consumer<UpdateWrapper<OrderInfo>> updateCustomizer) {

        OrderInfo currentOrder = orderInfoMapper.selectById(orderId);
        ResponseResult preflight = resolveLegacyTransitionRead(currentOrder, expectedStatus, targetStatus);
        if (preflight != null) {
            return preflight;
        }

        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderId)
                .eq("order_status", expectedStatus)
                .set("order_status", targetStatus);
        updateCustomizer.accept(updateWrapper);
        preserveLegacyOrderAuditTimes(updateWrapper);

        int updated = orderInfoMapper.update(null, updateWrapper);
        if (updated == 1) {
            return ResponseResult.success();
        }
        OrderInfo latestOrder = orderInfoMapper.selectById(orderId);
        ResponseResult rereadResult = resolveLegacyTransitionRead(latestOrder, expectedStatus, targetStatus);
        if (rereadResult != null) {
            return rereadResult;
        }
        return ResponseResult.fail(CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getCode(),
                CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getMessage());
    }

    private ResponseResult resolveLegacyTransitionRead(OrderInfo orderInfo, int expectedStatus, int targetStatus) {
        if (orderInfo == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        Integer currentStatus = orderInfo.getOrderStatus();
        if (isOrderStatus(currentStatus, targetStatus)) {
            return ResponseResult.success();
        }
        if (isFinalizationOwnedStatus(currentStatus)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_IN_PROGRESS.getCode(),
                    CommonStatus.FINALIZATION_IN_PROGRESS.getMessage());
        }
        if (!isOrderStatus(currentStatus, expectedStatus)) {
            return ResponseResult.fail(CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getCode(),
                    CommonStatus.ORDER_STATE_TRANSITION_NOT_ALLOWED.getMessage());
        }
        return null;
    }

    private void preserveOrderCreationTime(UpdateWrapper<OrderInfo> updateWrapper) {
        updateWrapper.setSql(PRESERVE_GMT_CREATE_SQL);
    }

    private void preserveLegacyOrderAuditTimes(UpdateWrapper<OrderInfo> updateWrapper) {
        updateWrapper.setSql(PRESERVE_GMT_CREATE_SQL);
        updateWrapper.setSql(PRESERVE_GMT_MODIFIED_SQL);
    }

    /**
     * This method is used to process the status of a passenger when they pay for an order
     * @param orderRequest
     * @return
     */
    public ResponseResult pay(OrderRequest orderRequest){

        Long orderId = orderRequest.getOrderId();
        return transitionLegacyOrderState(
                orderId,
                OrderConstant.TO_START_PAY,
                OrderConstant.SUCCESS_PAY,
                updateWrapper -> {
                });
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
        if (orderInfo == null) {
            return ResponseResult.fail(CommonStatus.ORDER_NOT_FOUND.getCode(),
                    CommonStatus.ORDER_NOT_FOUND.getMessage());
        }
        Integer orderStatus = orderInfo.getOrderStatus();
        if (isFinalizationOwnedStatus(orderStatus)) {
            return ResponseResult.fail(CommonStatus.FINALIZATION_IN_PROGRESS.getCode(),
                    CommonStatus.FINALIZATION_IN_PROGRESS.getMessage());
        }

        LocalDateTime cancelTime = LocalDateTime.now(clock);
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
        return transitionLegacyOrderState(
                orderId,
                OrderConstant.PASSENGER_GETOFF,
                OrderConstant.TO_START_PAY,
                updateWrapper -> {
                });

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
