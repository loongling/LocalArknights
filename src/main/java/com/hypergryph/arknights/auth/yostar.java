package com.hypergryph.arknights.auth;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static com.hypergryph.arknights.ArknightsApplication.LOGGER;

@RestController
@RequestMapping("/yostar")
public class yostar {

    private static String Key = "IxMMveJRWsxStJgX";

    private static final long CODE_TTL_MS = 10 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<String, CodeEntry> CODES = new ConcurrentHashMap<>();

    private static final class CodeEntry {
        final String code;
        final long expireAt;

        CodeEntry(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }

    private static String normalize(String account) {
        return account == null ? "" : account.trim().toLowerCase();
    }

    public static boolean mailerEnabled() {
        try {
            JSONObject mailer = ArknightsApplication.serverConfig.getJSONObject("yostar").getJSONObject("mailer");
            return mailer != null && mailer.getBooleanValue("enable");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验验证码（成功即消费掉）。
     * 供 /user/register、/user/forget_password 等流程调用。
     *
     * 【重要】mailer 未启用时直接放行 —— 没开邮件服务就不该拦注册。
     */
    public static boolean verifyCode(String account, String code) {
        if (!mailerEnabled()) {
            LOGGER.debug("[yostar] mailer 未启用，跳过验证码校验: {}", account);
            return true;
        }
        String key = normalize(account);
        if (key.isEmpty() || code == null) {
            return false;
        }
        CodeEntry entry = CODES.get(key);
        if (entry == null) {
            return false;
        }
        if (System.currentTimeMillis() > entry.expireAt) {
            CODES.remove(key);
            return false;
        }
        if (!entry.code.equals(code.trim())) {
            return false;
        }
        CODES.remove(key);
        return true;
    }

    /** 调试用：查看某账号当前验证码（不存在或已过期返回 null） */
    public static String peekCode(String account) {
        CodeEntry entry = CODES.get(normalize(account));
        if (entry == null || System.currentTimeMillis() > entry.expireAt) {
            return null;
        }
        return entry.code;
    }

    private static String randomCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static boolean looksLikeEmail(String account) {
        return account != null && account.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    @PostMapping(
            value = {"/send-code"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject SendCode(@RequestBody JSONObject sendCode, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[" + clientIp + "] /yostar/send-code");
        LOGGER.info("YoStar SendCode " + sendCode);

        JSONObject result = new JSONObject(true);
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            result.put("result", 4);
            result.put("errMsg", "server is closed");
            return result;
        }

        String account = sendCode.getString("Account");
        if (account == null || account.isEmpty()) {
            account = sendCode.getString("account");
        }
        if (account == null || account.isEmpty()) {
            result.put("result", 4);
            result.put("errMsg", "account is required");
            return result;
        }
        if (!looksLikeEmail(account)) {
            result.put("result", 4);
            result.put("errMsg", "invalid email address");
            return result;
        }

        String code = randomCode();
        CODES.put(normalize(account), new CodeEntry(code, System.currentTimeMillis() + CODE_TTL_MS));

        if (!mailerEnabled()) {
            LOGGER.warn("[yostar/send-code] mailer 未启用，验证码仅记录在日志: {} -> {}", account, code);
            result.put("Code", 200);
            result.put("Msg", "OK");
            result.put("Data", new JSONObject());
            return result;
        }

        JSONObject mailer;
        try {
            mailer = ArknightsApplication.serverConfig.getJSONObject("yostar").getJSONObject("mailer");
        } catch (Exception e) {
            mailer = null;
        }

        String smtp = mailer.getString("smtp");
        int port = mailer.getIntValue("smtp_port");
        String mailAccount = mailer.getString("account");
        String mailPassword = mailer.getString("password");
        if (smtp == null || smtp.isEmpty() || mailAccount == null || mailAccount.isEmpty() || port <= 0) {
            LOGGER.error("[yostar/send-code] mailer 配置不完整: smtp={} port={} account={}", smtp, port, mailAccount);
            CODES.remove(normalize(account));
            response.setStatus(500);
            result.put("result", 4);
            result.put("errMsg", "mailer config incomplete");
            return result;
        }

        try {
            sendMail(smtp, port, mailAccount, mailPassword, account, code);
            LOGGER.info("[yostar/send-code] 验证码已发送: {} (发件人 {})", account, mailAccount);
            result.put("Code", 200);
            result.put("Msg", "OK");
            result.put("Data", new JSONObject());
        } catch (Exception e) {
            CODES.remove(normalize(account));
            LOGGER.error("[yostar/send-code] 发信失败 -> " + account + " via " + smtp + ":" + port, e);
            response.setStatus(500);
            result.put("result", 4);
            result.put("errMsg", "send mail failed: " + e.getMessage());
        }
        return result;
    }

    private void sendMail(String host, int port, String from, String password,
                          String to, String code) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, password == null ? "" : password);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from, "Arknights"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        message.setSubject("Arknights 验证码 / Verification Code", "UTF-8");
        message.setSentDate(new java.util.Date());

        String html = "<div style=\"font-family:sans-serif;font-size:14px\">"
                + "<p>您的验证码是 / Your verification code is:</p>"
                + "<p style=\"font-size:28px;letter-spacing:6px;font-weight:bold\">" + code + "</p>"
                + "<p>10 分钟内有效，请勿转发给他人。</p>"
                + "<p style=\"color:#888\">该邮件由LocalArknights部署服务器自动发送，如非本人操作请忽略。</p>"
                + "</div>";
        message.setContent(html, "text/html;charset=UTF-8");

        Transport.send(message);
    }

    @PostMapping(
            value = {"/get-auth"},
            produces = {"application/json;charset=UTF-8"}
    )
    public JSONObject getAuth(@RequestBody JSONObject auth, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = ArknightsApplication.getIpAddr(request);
        LOGGER.info("[" + clientIp + "] /yostar/get-auth");
        LOGGER.info("YoStar auth " + auth);

        JSONObject result = new JSONObject(true);
        if (!ArknightsApplication.enableServer) {
            response.setStatus(400);
            result.put("result", 4);
            result.put("errMsg", "server is closed");
            return result;
        }
        String Account = auth.getString("Account");
        String Code = auth.getString("Code");
        String token = DigestUtils.md5DigestAsHex((Account + Key).getBytes());
        JSONObject data = new JSONObject();
        if (mailerEnabled()) {
            if (!verifyCode(Account, Code)) {
                response.setStatus(400);
                result.put("result", 4);
                result.put("errMsg", "code is not match");
            }
            else {
                result.put("Code", 200);
                result.put("Msg", "OK");
                data.put("UID", Account);
                data.put("Token", token);
                data.put("Account", Account);
                result.put("Data", data);
            }
        }
        else {
            result.put("Code", 200);
            result.put("Msg", "OK");
            data.put("UID", Account);
            data.put("Token", token);
            data.put("Account", Account);
            result.put("Data", data);
        }
        return  result;
    }
}
