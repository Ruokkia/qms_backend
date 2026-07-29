package com.kangli.qms.service.auth.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import com.kangli.qms.common.AuthConstants;
import com.kangli.qms.config.LoginProperties;
import com.kangli.qms.service.auth.CaptchaService;
import com.kangli.qms.util.RedisUtil;
import com.kangli.qms.domain.auth.vo.CaptchaVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 验证码服务实现。
 * <p>基于 hutool LineCaptcha 生成 4 位字母数字图形验证码，存 Redis（TTL 可配，默认 5min），
 * 校验通过即删防止重放攻击。</p>
 */
@Slf4j
@Service
public class CaptchaServiceImpl implements CaptchaService {

    private final RedisUtil redisUtil;
    private final LoginProperties loginProps;

    public CaptchaServiceImpl(RedisUtil redisUtil, LoginProperties loginProps) {
        this.redisUtil = redisUtil;
        this.loginProps = loginProps;
    }

    @Override
    public CaptchaVO generateCaptcha() {
        // 1. 生成 4 位字母数字图形验证码（宽130 高40 干扰线30条）
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(130, 40, 4, 30);
        String code = captcha.getCode();
        String base64Image = captcha.getImageBase64Data();

        // 2. 生成随机 key
        String captchaKey = IdUtil.fastSimpleUUID();

        // 3. 存入 Redis（TTL = captchaTtlSeconds）
        long ttl = loginProps.getCaptchaTtlSeconds();
        redisUtil.set(AuthConstants.captchaKey(captchaKey), code.toUpperCase(), ttl);

        log.debug("[验证码生成] key={}, ttl={}s", captchaKey, ttl);

        // 4. 组装响应
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaKey(captchaKey);
        vo.setCaptchaImage(base64Image);
        vo.setExpireIn(ttl);
        return vo;
    }

    @Override
    public boolean validateCaptcha(String captchaKey, String inputCode) {
        if (captchaKey == null || captchaKey.isEmpty() || inputCode == null || inputCode.isEmpty()) {
            return false;
        }

        String redisKey = AuthConstants.captchaKey(captchaKey);
        String storedCode = redisUtil.getString(redisKey);

        // key 不存在（过期或未生成）
        if (storedCode == null) {
            log.debug("[验证码校验] key={} 失败：验证码已过期或不存在", captchaKey);
            return false;
        }

        // 忽略大小写比对
        boolean matched = storedCode.equalsIgnoreCase(inputCode.trim());

        // 无论成功失败都删除（一次性，防重放；失败也删，强制用户重新获取）
        redisUtil.delete(redisKey);

        if (matched) {
            log.debug("[验证码校验] key={} 通过", captchaKey);
        } else {
            log.debug("[验证码校验] key={} 失败：验证码错误", captchaKey);
        }
        return matched;
    }
}
