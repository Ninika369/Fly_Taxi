package com.george.internalCommon.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.AlgorithmMismatchException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.TokenResult;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author: George Sun
 * @Date: 2024-10-18-16:10
 * @Description: This class is used to generate tokens with JWT format.
 */
public class JwtUtils {

    // private secrete code in JWT
    private static final String CODE = "CADCGg_Sun@!!$";

    // the phone number of user
    private static final String KEY_PHONE = "phone";

    // the identity of user, for instance, passenger or driver
    private static final String KEY_IDENTITY = "identity";

    // the type of token, refresh or access
    private static final String KEY_TYPE = "type";

    // the time when the token was created
    private static final String KEY_TIME = "time";

    /**
     * This function is used to generate an encoded token that can be sent to users
     * @param passengerPhone -
     * @return - an endoced token String
     */
    public static String generateToken(String passengerPhone, String identity, String type) {
        Map<String, String> map = new HashMap<>();
        map.put(KEY_PHONE, passengerPhone);
        map.put(KEY_IDENTITY, identity);
        map.put(KEY_TYPE, type);
        map.put(KEY_TIME, Calendar.getInstance().getTime().toString());

        JWTCreator.Builder builder = JWT.create();

        // send any pair in map to builder
        map.forEach((k, v) -> {
            builder.withClaim(k, v);
        });


        // generate token with specific algorithm
        String token = builder.sign(Algorithm.HMAC256(CODE));

        return token;
    }


    /**
     * This function is used to generate an decoded token
     * @param token - the encrypted token
     * @return - the decrypted message
     */
    public static TokenResult parseToken(String token) {
        DecodedJWT verify = JWT.require(Algorithm.HMAC256(CODE)).build().verify(token);
        String phone = verify.getClaim(KEY_PHONE).asString();
        String identity = verify.getClaim(KEY_IDENTITY).asString();
        TokenResult result = new TokenResult();
        result.setPhone(phone);
        result.setIdentity(identity);
        return result;
    }

    /**
     * This function is used to check the correctness of token
     * @param token
     * @return
     */
    public static TokenResult checkToken(String token) {
        TokenResult res = null;
        try {
            res = JwtUtils.parseToken(token);
        }
        catch (Exception e) {
        }
        return res;
    }

    public static void main(String[] args) {
        String token = generateToken("13304268325", "1", "accessToken");
        System.out.println("token is: " + token);
        TokenResult result = parseToken(token);
        System.out.println("decrypted message is: " + result.getPhone() + " | " + result.getIdentity());
        System.out.println(UserIdentity.PASSENGER.getIdentity());
    }

}
