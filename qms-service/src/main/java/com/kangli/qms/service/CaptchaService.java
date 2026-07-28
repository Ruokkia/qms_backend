package com.kangli.qms.service;

import com.kangli.qms.vo.CaptchaVO;

/**
 * 验证码服务。
 * <p>生成图形验证码 → 存 Redis（TTL 5min）→ 返回 base64 图片 + key；
 * 校验时比对（忽略大小写），通过即删防重放。</p>
 */
public interface CaptchaService {

    /**
     * 生成验证码：创建随机 key → 生成 4 位字母数字图形码 → 存 Redis → 返回 base64。
     *
     * @return 验证码 VO（key + base64 图片）
     */
    CaptchaVO generateCaptcha();

    /**
     * 校验验证码：从 Redis 取出 → 忽略大小写比对 → 通过即删（防重放）。
     *
     * @param captchaKey 验证码 key
     * @param inputCode  用户输入的验证码
     * @return true=校验通过，false=校验失败（key 不存在或验证码错误）
     */
    boolean validateCaptcha(String captchaKey, String inputCode);
}
