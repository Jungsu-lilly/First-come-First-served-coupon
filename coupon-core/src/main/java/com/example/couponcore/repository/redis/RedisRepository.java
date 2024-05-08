package com.example.couponcore.repository.redis;

import com.example.couponcore.exception.common.BaseException;
import com.example.couponcore.repository.redis.dto.CouponIssueRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.couponcore.util.CouponRedisUtils.getIssueRequestKey;
import static com.example.couponcore.util.CouponRedisUtils.getQueueKey;

@Repository
@RequiredArgsConstructor
public class RedisRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private RedisScript<String> issueScript = issueRequestScript();
    private final String issueRequestQueueKey = getQueueKey();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Boolean zAdd(String key, String value, double score) {
        return redisTemplate.opsForZSet().addIfAbsent(key, value, score);
    }

    public Long sAdd(String key, String value) {
        return redisTemplate.opsForSet().add(key, value);
    }

    public Long sCard(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    public Boolean sIsMember(String key, String value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    public Long rPush(String key, String value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    public Long lSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    public String lPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    public String lIndex(String key, long index) {
        return redisTemplate.opsForList().index(key, index);
    }

    public void issueRequest(long couponId, long userId, int totalIssueQuantity) {
        String issueRequestKey = getIssueRequestKey(couponId);
        CouponIssueRequest couponIssueRequest = new CouponIssueRequest(couponId, userId);
        try {
            String code = redisTemplate.execute(
                    issueScript,
                    List.of(issueRequestKey, issueRequestQueueKey),
                    String.valueOf(userId),
                    String.valueOf(totalIssueQuantity),
                    objectMapper.writeValueAsString(couponIssueRequest)
            );
            // validation 진행
            CouponIssueRequestCode.checkRequestResult(CouponIssueRequestCode.find(code));

        } catch (JsonProcessingException e) {
            throw new BaseException(400, e.getMessage());
        }
    }

    public RedisScript<String> issueRequestScript() {
        String script = """
                if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then
                    return '2'
                end
                   
                if tonumber(ARGV[2]) > redis.call('SCARD', KEYS[1]) then
                    redis.call('SADD', KEYS[1], ARGV[1])
                    redis.call('RPUSH', KEYS[2], ARGV[3])
                    return '1'
                end
                
                return '3'    
                """;
        return RedisScript.of(script, String.class);
    }
}
