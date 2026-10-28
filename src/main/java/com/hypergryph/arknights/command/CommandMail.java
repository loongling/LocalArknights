package com.hypergryph.arknights.command;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.core.mail.MailService;
import com.hypergryph.arknights.core.pojo.Mail;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * 邮件控制台命令。
 *
 * <pre>
 * /mail list                           列出 mail 表里的模板
 * /mail list &lt;uid&gt;                     列出某个玩家收到的邮件
 * /mail send &lt;uid&gt; &lt;模板id&gt; [天数]      按模板 id 发一封（天数默认 365，0=不过期）
 * /mail send &lt;uid&gt; name:&lt;模板name&gt; [天数]  按模板 name 发
 * /mail delete &lt;uid&gt; &lt;mailId&gt;          删掉玩家的某封邮件
 * /mail clear &lt;uid&gt;                    清空某个玩家的所有邮件
 * </pre>
 */
public class CommandMail extends CommandBase {

    private static final Logger LOGGER = LogManager.getLogger();

    public CommandMail() {
    }

    public String getCommandName() {
        return "mail";
    }

    public String getCommandUsage(ICommandSender sender) {
        return "<list|send|delete|clear> ...";
    }

    @Override
    public java.util.List<String> getCommandAliases() {
        return java.util.Collections.singletonList("/mail");
    }

    public String getCommandDescription() {
        return "邮件管理：查看模板 / 发邮件 / 删邮件";
    }

    public String getCommandExample() {
        return "/mail send 10000001 1 365";
    }

    public String getCommandExampleUsage() {
        return "把 mail 表 id=1 的模板发给 uid=10000001，有效期 365 天";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        try {
            int start = 0;
            if (args.length > 0
                    && (args[0].equalsIgnoreCase("mail") || args[0].equalsIgnoreCase("/mail"))) {
                start = 1;
            }
            if (args.length <= start) {
                printUsage();
                return;
            }
            String sub = args[start].toLowerCase();

            switch (sub) {
                case "list" -> {
                    if (args.length > start + 1) {
                        listPlayerMails(Long.parseLong(args[start + 1]));
                    } else {
                        listTemplates();
                    }
                }
                case "send" -> {
                    if (args.length < start + 3) {
                        LOGGER.info("用法: /mail send <uid> <模板id 或 name:模板名> [有效天数]");
                        return;
                    }
                    long uid = Long.parseLong(args[start + 1]);
                    String target = args[start + 2];
                    long days = args.length > start + 3 ? Long.parseLong(args[start + 3]) : 365;
                    doSend(uid, target, days);
                }
                case "delete", "del" -> {
                    if (args.length < start + 3) {
                        LOGGER.info("用法: /mail delete <uid> <mailId>");
                        return;
                    }
                    doDelete(Long.parseLong(args[start + 1]), Integer.parseInt(args[start + 2]));
                }
                case "clear" -> {
                    if (args.length < start + 2) {
                        LOGGER.info("用法: /mail clear <uid>");
                        return;
                    }
                    doClear(Long.parseLong(args[start + 1]));
                }
                default -> printUsage();
            }
        } catch (NumberFormatException e) {
            LOGGER.error("参数格式错误: uid / 模板id / mailId / 天数 必须是数字");
            printUsage();
        } catch (Exception e) {
            LOGGER.error("执行 mail 命令失败", e);
        }
    }

    private void listTemplates() {
        List<Mail> rows = MailService.templates();
        if (rows.isEmpty()) {
            LOGGER.info("mail 表是空的（模板库），先往表里插一行再发信");
            return;
        }
        LOGGER.info("mail 表里的模板（{} 个）:", rows.size());
        for (Mail m : rows) {
            String items = m.getItems() == null ? "[]" : m.getItems();
            if (items.length() > 120) {
                items = items.substring(0, 120) + "…";
            }
            LOGGER.info("  id={}  name={}  from={}  subject={}  items={}",
                    m.getId(), m.getName(), m.getFrom(), m.getSubject(), items);
        }
    }

    private void listPlayerMails(long uid) {
        JSONArray mails = MailService.mailsOf(uid);
        if (mails == null) {
            LOGGER.error("找不到玩家存档: uid={}", uid);
            return;
        }
        if (mails.isEmpty()) {
            LOGGER.info("uid={} 没有任何邮件", uid);
            return;
        }
        LOGGER.info("uid={} 有 {} 封邮件:", uid, mails.size());
        for (int i = 0; i < mails.size(); i++) {
            JSONObject m = mails.getJSONObject(i);
            JSONArray items = m.getJSONArray("items");
            LOGGER.info("  mailId={}  state={}  hasItem={}  主题={}  附件={}",
                    m.getIntValue("mailId"), m.getIntValue("state"), m.getIntValue("hasItem"),
                    m.getString("subject"), items == null ? 0 : items.size());
        }
    }

    private void doSend(long uid, String target, long days) {
        Integer templateId = null;
        String templateName = null;
        if (target.startsWith("name:")) {
            templateName = target.substring("name:".length());
        } else {
            templateId = Integer.parseInt(target);
        }
        JSONObject r = MailService.send(uid, templateId, templateName, days);
        if (r.getIntValue("result") == 0) {
            LOGGER.info("发送成功: uid={} mailId={} 主题={} 附件={} 个",
                    uid, r.getIntValue("mailId"), r.getString("subject"),
                    r.getJSONArray("items").size());
        } else {
            LOGGER.error("发送失败: {}", r.getString("error"));
        }
    }

    private void doDelete(long uid, int mailId) {
        int n = MailService.delete(uid, mailId);
        if (n < 0) {
            LOGGER.error("找不到玩家存档: uid={}", uid);
        } else if (n == 0) {
            LOGGER.error("uid={} 没有 mailId={} 这封邮件", uid, mailId);
        } else {
            LOGGER.info("已删除 uid={} 的 mailId={}", uid, mailId);
        }
    }

    private void doClear(long uid) {
        int n = MailService.clear(uid);
        if (n < 0) {
            LOGGER.error("找不到玩家存档: uid={}", uid);
        } else {
            LOGGER.info("已清空 uid={} 的 {} 封邮件", uid, n);
        }
    }

    private void printUsage() {
        LOGGER.info("邮件命令用法:");
        LOGGER.info("  /mail list                       列出 mail 表里的模板");
        LOGGER.info("  /mail list <uid>                 列出某玩家的邮件");
        LOGGER.info("  /mail send <uid> <模板id> [天数]   按模板 id 发信（默认 365 天，0=不过期）");
        LOGGER.info("  /mail send <uid> name:<模板名> [天数]  按模板 name 发信");
        LOGGER.info("  /mail delete <uid> <mailId>      删除玩家某封邮件");
        LOGGER.info("  /mail clear <uid>                清空玩家所有邮件");
    }
}
