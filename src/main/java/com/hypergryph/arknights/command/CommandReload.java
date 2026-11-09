package com.hypergryph.arknights.command;

import com.alibaba.fastjson.JSONObject;
import com.hypergryph.arknights.ArknightsApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandReload extends CommandBase {

    private static final Logger LOGGER = LogManager.getLogger();

    public CommandReload() {
    }

    public String getCommandName() {
        return "reload";
    }

    public String getCommandUsage(ICommandSender sender) {
        return "";
    }

    public String getCommandDescription() {
        return "重新加载全部 JSON（配置 / 表格 / 商店 / 券池）";
    }

    public String getCommandExample() {
        return "/reload";
    }

    public String getCommandExampleUsage() {
        return "修改 data/ 下任意 json 后执行，立即生效，无需重启";
    }

    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        try {
            LOGGER.info("开始热重载全部 JSON ...");
            long start = System.currentTimeMillis();
            ArknightsApplication.reloadServerConfig();
            long cost = System.currentTimeMillis() - start;

            JSONObject counts = new JSONObject(true);
            counts.put("character", ArknightsApplication.characterJson == null ? 0 : ArknightsApplication.characterJson.size());
            counts.put("item", ArknightsApplication.itemTable == null ? 0 : ArknightsApplication.itemTable.size());
            counts.put("stage", ArknightsApplication.stageTable == null ? 0 : ArknightsApplication.stageTable.size());
            counts.put("skin", ArknightsApplication.skinTable == null ? 0 : ArknightsApplication.skinTable.size());
            counts.put("allProduct", sizeOf(ArknightsApplication.AllProductList, "productList"));
            counts.put("skinGood", sizeOf(ArknightsApplication.skinGoodList, "goodList"));
            counts.put("cashGood", ArknightsApplication.CashGoodList == null ? 0 : ArknightsApplication.CashGoodList.size());
            counts.put("furniGood", sizeOf(ArknightsApplication.FurniGoodList, "goods"));
            counts.put("voucher", com.hypergryph.arknights.core.voucher.VoucherTable.size());

            LOGGER.info("热重载完成，耗时 {}ms：{}", cost, counts.toJSONString());
        } catch (Exception e) {
            LOGGER.error("热重载失败: {}", e.toString());
        }
    }

    private static int sizeOf(JSONObject obj, String arrayKey) {
        if (obj == null) {
            return 0;
        }
        JSONObject wrap = obj;
        com.alibaba.fastjson.JSONArray arr = wrap.getJSONArray(arrayKey);
        return arr == null ? 0 : arr.size();
    }
}
