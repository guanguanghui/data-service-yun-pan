package com.sxw.server.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.sxw.server.exception.BusinessException;
import com.sxw.server.pojo.TokenInfo;
import io.jsonwebtoken.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class TokenResolver {
    private static final Logger log = LoggerFactory.getLogger(TokenResolver.class);
    private static final String JWT_ID = "jwt";
    private static final String JWT_TOKEN_ISSUER = "sxjy";
    private static final Long TOKEN_CLOCK_SKEW_MILLIS = 0L;
    public static final Long MONTH_SECOND = Math.max(0L, 2592000L);
    public static final Long YEAR_SECOND = Math.max(0L, 31104000L);
    private static final String TOKEN_SECRET = "7786df7fc3a34e26a61c034d5ec8245d";
    public static final int TOKEN_NOT_FOUND_CODE = 8500;
    public static final String TOKEN_NOT_FOUND_MSG = "token not found.";
    public static final int TOKEN_ERROR_CODE = 8501;
    public static final String TOKEN_ERROR_MSG = "token is unknown error.";
    public static final int TOKEN_EXPIRED_CODE = 8502;
    public static final String TOKEN_EXPIRED_MSG = "token have expired.";
    public static final int TOKEN_SECRET_ERROR_CODE = 8503;
    public static final String TOKEN_SECRET_ERROR_MSG = "token signature error.";
    public static final int TOKEN_UN_SUPPORTED_CODE = 8504;
    public static final String TOKEN_UN_SUPPORTED_MSG = "unsupported token string.";
    public static final int TOKEN_INVALID_CODE = 8505;
    public static final String TOKEN_INVALID_MSG = "token is invalid.";

    public TokenResolver() {
    }

    private static String converterSecret(String tokenSecret) {
        return StringUtils.isNotBlank(tokenSecret) ? tokenSecret : "7786df7fc3a34e26a61c034d5ec8245d";
    }

    public static String createToken(String subject, Long validityInSeconds) {
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        Date validity = new Date(System.currentTimeMillis() + 1000L * validityInSeconds);
        return Jwts.builder().setId("jwt").setSubject(subject).signWith(signatureAlgorithm, "7786df7fc3a34e26a61c034d5ec8245d").setExpiration(validity).setIssuer("sxjy").compact();
    }

    public static boolean validateToken(String authToken) {
        String tokenSecret = converterSecret("7786df7fc3a34e26a61c034d5ec8245d");
        resolve(authToken, tokenSecret, TOKEN_CLOCK_SKEW_MILLIS);
        return true;
    }

    private static TokenInfo resolveTokenInfo(String tokenValue) {
        try {
            TokenInfo tokenInfo = (TokenInfo) JSON.parseObject(tokenValue, TokenInfo.class);
            return tokenInfo;
        } catch (JSONException var3) {
            throw (new BusinessException(8505, "token is invalid.", (Object)null, var3)).info();
        }
    }

    public static TokenInfo readTokenInfo(String tokenString) {
        return resolveTokenInfo(resolve(tokenString, "7786df7fc3a34e26a61c034d5ec8245d", TOKEN_CLOCK_SKEW_MILLIS));
    }

    public static TokenInfo readTokenInfo(String tokenString, Long clockSkewMillis) {
        return resolveTokenInfo(resolve(tokenString, "7786df7fc3a34e26a61c034d5ec8245d", clockSkewMillis));
    }

    public static String readTokenValue(String tokenString) {
        return resolve(tokenString, "7786df7fc3a34e26a61c034d5ec8245d", TOKEN_CLOCK_SKEW_MILLIS);
    }

    public static String readTokenValue(String tokenString, Long clockSkewMillis) {
        return resolve(tokenString, "7786df7fc3a34e26a61c034d5ec8245d", clockSkewMillis);
    }

    private static String resolve(String authToken, String tokenSecret, Long tokenClockSkewMillis) {
        if (StringUtils.isBlank(authToken)) {
            throw (new BusinessException(8500, "token not found.", authToken, (Throwable)null)).info();
        } else {
            try {
                tokenSecret = converterSecret(tokenSecret);
                Claims claims = (Claims)Jwts.parser().setSigningKey(tokenSecret).setAllowedClockSkewSeconds(tokenClockSkewMillis).parseClaimsJws(authToken).getBody();
                String tokenValue = claims.getSubject();
                return tokenValue;
            } catch (IllegalArgumentException var5) {
                throw (new BusinessException(8500, "token not found.", authToken, (Throwable)null)).info();
            } catch (UnsupportedJwtException var6) {
                log.error("ValidateToken error UnsupportedJwtException: {}", var6.getMessage());
                throw (new BusinessException(8504, "unsupported token string.", authToken, (Throwable)null)).info();
            } catch (MalformedJwtException var7) {
                log.error("ValidateToken error MalformedJwtException: {}", var7.getMessage());
                throw (new BusinessException(8505, "token is invalid.", authToken, (Throwable)null)).info();
            } catch (SignatureException var8) {
                log.error("ValidateToken error signatureException: {}", var8.getMessage());
                throw (new BusinessException(8503, "token signature error.", authToken, (Throwable)null)).info();
            } catch (ExpiredJwtException var9) {
                log.error("ValidateToken error expiredJwtException: {}", var9.getMessage());
                throw (new BusinessException(8502, "token have expired.", authToken, (Throwable)null)).info();
            } catch (Exception var10) {
                log.error("ValidateToken error runtimeException: {}", var10.getMessage());
                throw (new BusinessException(8501, "token is unknown error.", authToken, (Throwable)null)).info();
            }
        }
    }
}
