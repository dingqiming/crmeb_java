package com.zbkj.crmeb.payment.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.common.MyRecord;
import com.constants.*;
import com.exception.CrmebException;
import com.utils.DateUtil;
import com.utils.RedisUtil;
import com.zbkj.crmeb.bargain.service.StoreBargainService;
import com.zbkj.crmeb.bargain.service.StoreBargainUserService;
import com.zbkj.crmeb.combination.model.StoreCombination;
import com.zbkj.crmeb.combination.model.StorePink;
import com.zbkj.crmeb.combination.service.StoreCombinationService;
import com.zbkj.crmeb.combination.service.StorePinkService;
import com.zbkj.crmeb.front.request.OrderPayRequest;
import com.zbkj.crmeb.front.response.OrderPayResultResponse;
import com.zbkj.crmeb.front.vo.WxPayJsResultVo;
import com.zbkj.crmeb.marketing.model.StoreCouponUser;
import com.zbkj.crmeb.marketing.service.StoreCouponService;
import com.zbkj.crmeb.marketing.service.StoreCouponUserService;
import com.zbkj.crmeb.payment.service.OrderPayService;
import com.zbkj.crmeb.payment.vo.wechat.PayParamsVo;
import com.zbkj.crmeb.payment.wechat.WeChatPayService;
import com.zbkj.crmeb.sms.service.SmsService;
import com.zbkj.crmeb.store.model.StoreOrder;
import com.zbkj.crmeb.store.model.StoreProduct;
import com.zbkj.crmeb.store.model.StoreProductAttrValue;
import com.zbkj.crmeb.store.model.StoreProductCoupon;
import com.zbkj.crmeb.store.service.*;
import com.zbkj.crmeb.store.utilService.OrderUtils;
import com.zbkj.crmeb.store.vo.StoreOrderInfoOldVo;
import com.zbkj.crmeb.system.model.SystemAdmin;
import com.zbkj.crmeb.system.service.SystemAdminService;
import com.zbkj.crmeb.system.service.SystemConfigService;
import com.zbkj.crmeb.user.model.*;
import com.zbkj.crmeb.user.service.*;
import com.zbkj.crmeb.wechat.service.TemplateMessageService;
import com.zbkj.crmeb.wechat.service.WeChatService;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * OrderPayService ?????????
 * +----------------------------------------------------------------------
 * | CRMEB [ CRMEB???????????????????????????????????? ]
 * +----------------------------------------------------------------------
 * | Copyright (c) 2016~2020 https://www.crmeb.com All rights reserved.
 * +----------------------------------------------------------------------
 * | Licensed CRMEB????????????????????????????????????????????????CRMEB????????????
 * +----------------------------------------------------------------------
 * | Author: CRMEB Team <admin@crmeb.com>
 * +----------------------------------------------------------------------
 */
@Data
@Service
public class OrderPayServiceImpl implements OrderPayService {
    private static final Logger logger = LoggerFactory.getLogger(OrderPayServiceImpl.class);

    @Autowired
    private StoreOrderService storeOrderService;

    @Autowired
    private StoreOrderStatusService storeOrderStatusService;

    @Autowired
    private StoreOrderInfoService storeOrderInfoService;

    @Lazy
    @Autowired
    private WeChatPayService weChatPayService;

    @Autowired
    private TemplateMessageService templateMessageService;

    @Autowired
    private UserBillService userBillService;

    @Lazy
    @Autowired
    private SmsService smsService;

    @Autowired
    private UserService userService;

    @Autowired
    private StoreProductCouponService storeProductCouponService;

    @Autowired
    private StoreCouponUserService storeCouponUserService;

    @Autowired
    private WeChatService weChatService;

    @Autowired
    private OrderUtils orderUtils;

    //?????????
    private StoreOrder order;

    //??????????????????
    private PayParamsVo payParamsVo;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private StoreProductService storeProductService;

    @Autowired
    private UserLevelService userLevelService;

    @Autowired
    private StoreBargainService storeBargainService;

    @Autowired
    private StoreBargainUserService storeBargainUserService;

    @Autowired
    private StoreCombinationService storeCombinationService;

    @Autowired
    private StorePinkService storePinkService;

    @Autowired
    private UserBrokerageRecordService userBrokerageRecordService;

    @Autowired
    private StoreCouponService storeCouponService;

    @Autowired
    private SystemAdminService systemAdminService;

    @Autowired
    private UserTokenService userTokenService;

    @Autowired
    private UserIntegralRecordService userIntegralRecordService;

    @Autowired
    private StoreProductAttrValueService storeProductAttrValueService;

    /**
     * ??????????????????
     * @param storeOrder ??????
     */
    @Override
    public Boolean paySuccess(StoreOrder storeOrder) {

        User user = userService.getById(storeOrder.getUid());

        List<UserBill> billList = CollUtil.newArrayList();
        List<UserIntegralRecord> integralList = CollUtil.newArrayList();

        // ??????????????????
        UserBill userBill = userBillInit(storeOrder, user);
        billList.add(userBill);

        // ??????????????????
        if (storeOrder.getUseIntegral() > 0) {
            UserIntegralRecord integralRecordSub = integralRecordSubInit(storeOrder, user);
            integralList.add(integralRecordSub);
        }

        // ???????????????1.???????????????2.????????????
        Integer experience;
        experience = storeOrder.getPayPrice().setScale(0, BigDecimal.ROUND_DOWN).intValue();
        user.setExperience(user.getExperience() + experience);
        // ??????????????????
        UserBill experienceBill = experienceBillInit(storeOrder, user.getExperience(), experience);
        billList.add(experienceBill);

        // ???????????????1.?????????????????????2.??????????????????
        int integral;
        // ??????????????????
        //??????????????????
        String integralStr = systemConfigService.getValueByKey(Constants.CONFIG_KEY_INTEGRAL_RATE_ORDER_GIVE);
        if (StrUtil.isNotBlank(integralStr)) {
            BigDecimal integralBig = new BigDecimal(integralStr);
            integral = integralBig.multiply(storeOrder.getPayPrice()).setScale(0, BigDecimal.ROUND_DOWN).intValue();
            if (integral > 0) {
                // ??????????????????
                UserIntegralRecord integralRecord = integralRecordInit(storeOrder, user.getIntegral(), integral, "order");
                integralList.add(integralRecord);
            }
        }

        // ??????????????????
        // ??????????????????
        // ??????????????????????????????
        List<StoreOrderInfoOldVo> orderInfoList = storeOrderInfoService.getOrderListByOrderId(storeOrder.getId());
        List<Integer> productIds = orderInfoList.stream().map(StoreOrderInfoOldVo::getProductId).collect(Collectors.toList());
        if(productIds.size() > 0){
            List<StoreProduct> products = storeProductService.getListInIds(productIds);
            int sumIntegral = products.stream().mapToInt(StoreProduct::getGiveIntegral).sum();
            if (sumIntegral > 0) {
                // ??????????????????
                UserIntegralRecord integralRecord = integralRecordInit(storeOrder, user.getIntegral(), sumIntegral, "product");
                integralList.add(integralRecord);
            }
        }

        // ????????????????????????
        user.setPayCount(user.getPayCount() + 1);

        /**
         * ?????????????????????????????????
         */
        List<UserBrokerageRecord> recordList = assignCommission(storeOrder);

        Boolean execute = transactionTemplate.execute(e -> {
            //????????????
            storeOrderStatusService.addLog(storeOrder.getId(), Constants.ORDER_LOG_PAY_SUCCESS, Constants.ORDER_LOG_MESSAGE_PAY_SUCCESS);

            // ??????????????????
            userService.updateById(user);

            //????????????
            userBillService.saveBatch(billList);

            // ????????????
            userIntegralRecordService.saveBatch(integralList);

            //????????????
            userLevelService.upLevel(user);

            // ????????????
            if (CollUtil.isNotEmpty(recordList)) {
                recordList.forEach(temp -> {
                    temp.setLinkId(storeOrder.getOrderId());
                });
                userBrokerageRecordService.saveBatch(recordList);
            }

            // ??????????????????????????????????????????
            if (storeOrder.getBargainId() > 0) {
//                StoreBargainUser storeBargainUser = storeBargainUserService.getByBargainIdAndUid(storeOrder.getBargainId(), user.getUid());
//                storeBargainUser.setStatus(3);
//                storeBargainUserService.updateById(storeBargainUser);
            }

            // ?????????????????????????????????????????????
            if (storeOrder.getCombinationId() > 0) {
                pinkProcessing(storeOrder);
            }
            return Boolean.TRUE;
        });

        if (execute) {
            try {
                // ????????????
                if (StrUtil.isNotBlank(user.getPhone())) {
                    // ????????????????????????
                    String lowerOrderSwitch = systemConfigService.getValueByKey(SmsConstants.SMS_CONFIG_LOWER_ORDER_SWITCH);
                    if (StrUtil.isNotBlank(lowerOrderSwitch) && lowerOrderSwitch.equals("1")) {
                        smsService.sendPaySuccess(user.getPhone(), storeOrder.getOrderId(), storeOrder.getPayPrice());
                    }
                }

                // ?????????????????????????????????????????????
                String smsSwitch = systemConfigService.getValueByKey(SmsConstants.SMS_CONFIG_ADMIN_PAY_SUCCESS_SWITCH);
                if (StrUtil.isNotBlank(smsSwitch) && smsSwitch.equals("1")) {
                    // ????????????????????????????????????
                    List<SystemAdmin> systemAdminList = systemAdminService.findIsSmsList();
                    if (CollUtil.isNotEmpty(systemAdminList)) {
                        // ????????????
                        systemAdminList.forEach(admin -> {
                            smsService.sendOrderPaySuccessNotice(admin.getPhone(), storeOrder.getOrderId(), admin.getRealName());
                        });
                    }
                }

                //??????????????????
                pushMessageOrder(storeOrder, user);

                // ???????????????????????????????????????
                autoSendCoupons(storeOrder);

            } catch (Exception e) {
                e.printStackTrace();
                logger.error("???????????????????????????????????????");
            }
        }
        return execute;
    }

    // ??????????????????????????????
    private Boolean pinkProcessing(StoreOrder storeOrder) {
        // ????????????????????????
        StorePink storePink = storePinkService.getById(storeOrder.getPinkId());

        if (storePink.getKId() <= 0) {
            return true;
        }

        List<StorePink> pinkList = storePinkService.getListByCidAndKid(storePink.getCid(), storePink.getKId());
        StorePink tempPink = storePinkService.getById(storePink.getKId());
        pinkList.add(tempPink);
        if (pinkList.size() < storePink.getPeople()) {// ??????????????????
            return true;
        }
        // 1.??????????????????
        // 2.?????????????????????????????????????????????
        pinkList.forEach(e -> {
            e.setStatus(2);
        });
        boolean update = storePinkService.updateBatchById(pinkList);
        if (!update) {
            logger.error("???????????????????????????????????????????????????,orderNo = " + storeOrder.getOrderId());
            return false;
        }
        pinkList.forEach(i -> {
            StoreOrder order = storeOrderService.getByOderId(i.getOrderId());
            StoreCombination storeCombination = storeCombinationService.getById(i.getCid());
            User tempUser = userService.getById(i.getUid());
            // ????????????????????????
            MyRecord record = new MyRecord();
            record.set("orderNo", order.getOrderId());
            record.set("proName", storeCombination.getTitle());
            record.set("payType", order.getPayType());
            record.set("isChannel", order.getIsChannel());
            pushMessagePink(record, tempUser);
        });
        return true;
    }

    /**
     * ????????????????????????
     * @param record ????????????
     * @param user ??????
     */
    private void pushMessagePink(MyRecord record, User user) {
        if (!record.getStr("payType").equals(Constants.PAY_TYPE_WE_CHAT)) {
            return ;
        }
        if (record.getInt("isChannel").equals(2)) {
            return ;
        }

        UserToken userToken;
        HashMap<String, String> temMap = new HashMap<>();
        // ?????????
        if (record.getInt("isChannel").equals(Constants.ORDER_PAY_CHANNEL_PUBLIC)) {
            userToken = userTokenService.getTokenByUserId(user.getUid(), UserConstants.USER_TOKEN_TYPE_WECHAT);
            if (ObjectUtil.isNull(userToken)) {
                return ;
            }
            // ????????????????????????
            temMap.put(Constants.WE_CHAT_TEMP_KEY_FIRST, "??????????????????????????????????????????????????????");
            temMap.put("keyword1", record.getStr("orderNo"));
            temMap.put("keyword2", record.getStr("proName"));
            temMap.put(Constants.WE_CHAT_TEMP_KEY_END, "?????????????????????");
            templateMessageService.pushTemplateMessage(Constants.WE_CHAT_TEMP_KEY_COMBINATION_SUCCESS, temMap, userToken.getToken());
            return;
        }
        // ???????????????????????????
        userToken = userTokenService.getTokenByUserId(user.getUid(), UserConstants.USER_TOKEN_TYPE_ROUTINE);
        if (ObjectUtil.isNull(userToken)) {
            return ;
        }
        // ????????????
        temMap.put("character_string1",  record.getStr("orderNo"));
        temMap.put("thing2", record.getStr("proName"));
        temMap.put("thing5", "??????????????????????????????????????????????????????");
        templateMessageService.pushMiniTemplateMessage(Constants.WE_CHAT_PROGRAM_TEMP_KEY_COMBINATION_SUCCESS, temMap, userToken.getToken());

    }

    /**
     * ????????????
     * @param storeOrder ??????
     * @return List<UserBrokerageRecord>
     */
    private List<UserBrokerageRecord> assignCommission(StoreOrder storeOrder) {
        // ????????????????????????????????????
        String isOpen = systemConfigService.getValueByKey(Constants.CONFIG_KEY_STORE_BROKERAGE_IS_OPEN);
        if(StrUtil.isBlank(isOpen) || isOpen.equals("0")){
            return CollUtil.newArrayList();
        }
        // ?????????????????????
        if(storeOrder.getCombinationId() > 0 || storeOrder.getSeckillId() > 0 || storeOrder.getBargainId() > 0){
            return CollUtil.newArrayList();
        }
        // ???????????????????????????
        User user = userService.getById(storeOrder.getUid());
        // ????????????????????? ???????????? ?????? ???????????????????????????  ????????????
        if(null == user.getSpreadUid() || user.getSpreadUid() < 1 || user.getSpreadUid().equals(storeOrder.getUid())){
            return CollUtil.newArrayList();
        }
        // ????????????????????????????????????
        List<MyRecord> spreadRecordList = getSpreadRecordList(user.getSpreadUid());
        if (CollUtil.isEmpty(spreadRecordList)) {
            return CollUtil.newArrayList();
        }
        // ?????????????????????
        String fronzenTime = systemConfigService.getValueByKey(Constants.CONFIG_KEY_STORE_BROKERAGE_EXTRACT_TIME);

        // ??????????????????
        List<UserBrokerageRecord> brokerageRecordList = spreadRecordList.stream().map(record -> {
            BigDecimal brokerage = calculateCommission(record, storeOrder.getId());
            UserBrokerageRecord brokerageRecord = new UserBrokerageRecord();
            brokerageRecord.setUid(record.getInt("spreadUid"));
            brokerageRecord.setLinkType(BrokerageRecordConstants.BROKERAGE_RECORD_LINK_TYPE_ORDER);
            brokerageRecord.setType(BrokerageRecordConstants.BROKERAGE_RECORD_TYPE_ADD);
            brokerageRecord.setTitle(BrokerageRecordConstants.BROKERAGE_RECORD_TITLE_ORDER);
            brokerageRecord.setPrice(brokerage);
            brokerageRecord.setMark(StrUtil.format("???????????????????????????{}", brokerage));
            brokerageRecord.setStatus(BrokerageRecordConstants.BROKERAGE_RECORD_STATUS_CREATE);
            brokerageRecord.setFrozenTime(Integer.valueOf(Optional.ofNullable(fronzenTime).orElse("0")));
            brokerageRecord.setCreateTime(DateUtil.nowDateTime());
            return brokerageRecord;
        }).collect(Collectors.toList());

        return brokerageRecordList;
    }

    /**
     * ????????????
     * @param record index-???????????????spreadUid-?????????
     * @param orderId ??????id
     * @return BigDecimal
     */
    private BigDecimal calculateCommission(MyRecord record, Integer orderId) {
        BigDecimal brokeragePrice = BigDecimal.ZERO;
        // ??????????????????
        List<StoreOrderInfoOldVo> orderInfoVoList = storeOrderInfoService.getOrderListByOrderId(orderId);
        if (CollUtil.isEmpty(orderInfoVoList)) {
            return brokeragePrice;
        }
        BigDecimal totalBrokerPrice = BigDecimal.ZERO;
        //?????????????????????????????????
        Integer index = record.getInt("index");
        String key = "";
        if (index == 1) {
            key = Constants.CONFIG_KEY_STORE_BROKERAGE_RATE_ONE;
        }
        if (index == 2) {
            key = Constants.CONFIG_KEY_STORE_BROKERAGE_RATE_TWO;
        }
        String rate = systemConfigService.getValueByKey(key);
        if(StringUtils.isBlank(rate)){
            rate = "1";
        }
        //??????????????????????????? ??????80??? ?????????????????????????????? 10*10
        BigDecimal rateBigDecimal = brokeragePrice;
        if(StringUtils.isNotBlank(rate)){
            rateBigDecimal = new BigDecimal(rate).divide(BigDecimal.TEN.multiply(BigDecimal.TEN));
        }

        for (StoreOrderInfoOldVo orderInfoVo : orderInfoVoList) {
            // ?????????????????????????????????
            StoreProductAttrValue attrValue = storeProductAttrValueService.getById(orderInfoVo.getInfo().getAttrValueId());
            if (orderInfoVo.getInfo().getIsSub()) {// ???????????????
                if(index == 1){
                    brokeragePrice = Optional.ofNullable(attrValue.getBrokerage()).orElse(BigDecimal.ZERO);
                }
                if(index == 2){
                    brokeragePrice = Optional.ofNullable(attrValue.getBrokerageTwo()).orElse(BigDecimal.ZERO);
                }
            } else {// ????????????
                if(!rateBigDecimal.equals(BigDecimal.ZERO)){
                    // ????????????????????????, ??????????????????????????????????????????
                    // ???????????????????????????
                    brokeragePrice = orderInfoVo.getInfo().getPrice().multiply(rateBigDecimal).setScale(2, BigDecimal.ROUND_DOWN);
                } else {
                    brokeragePrice = BigDecimal.ZERO;
                }
            }
            // ??????????????????????????????
            if (brokeragePrice.compareTo(BigDecimal.ZERO) > 0 && orderInfoVo.getInfo().getPayNum() > 1) {
                brokeragePrice = brokeragePrice.multiply(new BigDecimal(orderInfoVo.getInfo().getPayNum()));
            }
            totalBrokerPrice = totalBrokerPrice.add(brokeragePrice);
        }

        return totalBrokerPrice;
    }

    /**
     * ????????????????????????????????????
     * @param spreadUid ???????????????Uid
     * @return List<MyRecord>
     */
    private List<MyRecord> getSpreadRecordList(Integer spreadUid) {
        List<MyRecord> recordList = CollUtil.newArrayList();

        // ?????????
        User spreadUser = userService.getById(spreadUid);
        if (ObjectUtil.isNull(spreadUser)) {
            return recordList;
        }
        // ??????????????????
        String model = systemConfigService.getValueByKey(Constants.CONFIG_KEY_STORE_BROKERAGE_MODEL);
        if (StrUtil.isNotBlank(model) && model.equals("1") && !spreadUser.getIsPromoter()) {
            // ??????????????????????????????????????????????????????
            return recordList;
        }
        MyRecord firstRecord = new MyRecord();
        firstRecord.set("index", 1);
        firstRecord.set("spreadUid", spreadUid);
        recordList.add(firstRecord);

        // ?????????
        User spreadSpreadUser = userService.getById(spreadUser.getSpreadUid());
        if (ObjectUtil.isNull(spreadSpreadUser)) {
            return recordList;
        }
        if (StrUtil.isNotBlank(model) && model.equals("1") && !spreadSpreadUser.getIsPromoter()) {
            // ??????????????????????????????????????????????????????
            return recordList;
        }
        MyRecord secondRecord = new MyRecord();
        secondRecord.set("index", 2);
        secondRecord.set("spreadUid", spreadSpreadUser.getUid());
        recordList.add(secondRecord);
        return recordList;
    }

    /**
     * ????????????
     * @param storeOrder ??????
     * @return Boolean Boolean
     */
    @Override
    public Boolean yuePay(StoreOrder storeOrder) {

        // ??????????????????
        User user = userService.getById(storeOrder.getUid());
        if (ObjectUtil.isNull(user)) throw new CrmebException("???????????????");
        if (user.getNowMoney().compareTo(storeOrder.getPayPrice()) < 0) {
            throw new CrmebException("??????????????????");
        }
        if (user.getIntegral() < storeOrder.getUseIntegral()) {
            throw new CrmebException("??????????????????");
        }
        storeOrder.setPaid(true);
        storeOrder.setPayTime(DateUtil.nowDateTime());
        Boolean execute = transactionTemplate.execute(e -> {
            // ????????????
            storeOrderService.updateById(storeOrder);
            // ???????????????????????????????????????task?????????
            userService.updateNowMoney(user, storeOrder.getPayPrice(), "sub");
            // ????????????
            if (storeOrder.getUseIntegral() > 0) {
                userService.updateIntegral(user, storeOrder.getUseIntegral(), "sub");
            }
            // ??????????????????redis??????
            redisUtil.lPush(Constants.ORDER_TASK_PAY_SUCCESS_AFTER, storeOrder.getOrderId());

            // ????????????
            if (storeOrder.getCombinationId() > 0) {
                // ??????????????????????????????
                StorePink headPink = new StorePink();
                Integer pinkId = storeOrder.getPinkId();
                if (pinkId > 0) {
                    headPink = storePinkService.getById(pinkId);
                    if (ObjectUtil.isNull(headPink) || headPink.getIsRefund().equals(true) || headPink.getStatus() == 3) {
                        pinkId = 0;
                    }
                }
                StoreCombination storeCombination = storeCombinationService.getById(storeOrder.getCombinationId());
                // ???????????????????????????????????????
                if (pinkId > 0) {
                    Integer count = storePinkService.getCountByKid(pinkId);
                    if (count >= storeCombination.getPeople()) {
                        pinkId = 0;
                    }
                }
                // ?????????????????????
                StorePink storePink = new StorePink();
                storePink.setUid(user.getUid());
                storePink.setAvatar(user.getAvatar());
                storePink.setNickname(user.getNickname());
                storePink.setOrderId(storeOrder.getOrderId());
                storePink.setOrderIdKey(storeOrder.getId());
                storePink.setTotalNum(storeOrder.getTotalNum());
                storePink.setTotalPrice(storeOrder.getTotalPrice());
                storePink.setCid(storeCombination.getId());
                storePink.setPid(storeCombination.getProductId());
                storePink.setPeople(storeCombination.getPeople());
                storePink.setPrice(storeCombination.getPrice());
                Integer effectiveTime = storeCombination.getEffectiveTime();// ???????????????
                DateTime dateTime = cn.hutool.core.date.DateUtil.date();
                storePink.setAddTime(dateTime.getTime());
                if (pinkId > 0) {
                    storePink.setStopTime(headPink.getStopTime());
                } else {
                    DateTime hourTime = cn.hutool.core.date.DateUtil.offsetHour(dateTime, effectiveTime);
                    long stopTime =  hourTime.getTime();
                    if (stopTime > storeCombination.getStopTime()) {
                        stopTime = storeCombination.getStopTime();
                    }
                    storePink.setStopTime(stopTime);
                }
                storePink.setKId(pinkId);
                storePink.setIsTpl(false);
                storePink.setIsRefund(false);
                storePink.setStatus(1);
                storePinkService.save(storePink);
                // ??????????????????????????????????????????
                storeOrder.setPinkId(storePink.getId());
                storeOrderService.updateById(storeOrder);
            }

            return Boolean.TRUE;
        });
        if (!execute) throw new CrmebException("????????????????????????");
        return execute;
    }

    /**
     * ????????????
     * @param orderPayRequest   ????????????
     * @param ip                ip
     * @return OrderPayResultResponse
     * 1.????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     * 2.???????????????????????????????????????????????????????????????task
     */
    @Override
    public OrderPayResultResponse payment(OrderPayRequest orderPayRequest, String ip) {
        StoreOrder storeOrder = storeOrderService.getByOderId(orderPayRequest.getOrderNo());
        if (ObjectUtil.isNull(storeOrder)) {
            throw new CrmebException("???????????????");
        }
        if (storeOrder.getIsDel()) {
            throw new CrmebException("??????????????????");
        }
        if (storeOrder.getPaid()) {
            throw new CrmebException("???????????????");
        }
        User user = userService.getById(storeOrder.getUid());
        if (ObjectUtil.isNull(user)) throw new CrmebException("???????????????");

        // ?????????????????????????????????????????????
        if (!storeOrder.getPayType().equals(orderPayRequest.getPayType())) {
            // ??????????????????????????????,??????????????????
            storeOrder.setPayType(orderPayRequest.getPayType());
            // ????????????
            if (orderPayRequest.getPayType().equals(PayConstants.PAY_TYPE_YUE)) {
                if (user.getNowMoney().compareTo(storeOrder.getPayPrice()) < 0) {
                    throw new CrmebException("??????????????????");
                }
                storeOrder.setIsChannel(3);
            }
            if (orderPayRequest.getPayType().equals(PayConstants.PAY_TYPE_WE_CHAT)) {
                switch (orderPayRequest.getPayChannel()){
                    case PayConstants.PAY_CHANNEL_WE_CHAT_H5:// H5
                        storeOrder.setIsChannel(2);
                        break;
                    case PayConstants.PAY_CHANNEL_WE_CHAT_PUBLIC:// ?????????
                        storeOrder.setIsChannel(0);
                        break;
                    case PayConstants.PAY_CHANNEL_WE_CHAT_PROGRAM:// ?????????
                        storeOrder.setIsChannel(1);
                        break;
                }
            }

            boolean changePayType = storeOrderService.updateById(storeOrder);
            if (!changePayType) {
                throw new CrmebException("??????????????????????????????!");
            }
        }

        if (user.getIntegral() < storeOrder.getUseIntegral()) {
            throw new CrmebException("??????????????????");
        }

        OrderPayResultResponse response = new OrderPayResultResponse();
        response.setOrderNo(storeOrder.getOrderId());
        response.setPayType(storeOrder.getPayType());
        // 0??????
        if (storeOrder.getPayPrice().compareTo(BigDecimal.ZERO) <= 0) {
            Boolean aBoolean = yuePay(storeOrder);
            response.setPayType(PayConstants.PAY_TYPE_YUE);
            response.setStatus(aBoolean);
            return response;
        }

        // ??????????????????????????????????????????????????????????????????????????????
        if (storeOrder.getPayType().equals(PayConstants.PAY_TYPE_WE_CHAT)) {
            Map<String, String> unifiedorder = weChatPayService.unifiedorder(storeOrder, ip);
            response.setStatus(true);
            WxPayJsResultVo vo = new WxPayJsResultVo();
            vo.setAppId(unifiedorder.get("appId"));
            vo.setNonceStr(unifiedorder.get("nonceStr"));
            vo.setPackages(unifiedorder.get("package"));
            vo.setSignType(unifiedorder.get("signType"));
            vo.setTimeStamp(unifiedorder.get("timeStamp"));
            vo.setPaySign(unifiedorder.get("paySign"));
            if (storeOrder.getIsChannel() == 2) {
                vo.setMwebUrl(unifiedorder.get("mweb_url"));
                response.setPayType(PayConstants.PAY_CHANNEL_WE_CHAT_H5);
            }
            response.setJsConfig(vo);
            return response;
        }
        // ????????????
        if (storeOrder.getPayType().equals(PayConstants.PAY_TYPE_YUE)) {
            Boolean yueBoolean = yuePay(storeOrder);
            response.setStatus(yueBoolean);
            return response;
        }
        if (storeOrder.getPayType().equals(PayConstants.PAY_TYPE_ALI_PAY)) {
            throw new CrmebException("??????????????????????????????");
        }
        if (storeOrder.getPayType().equals(PayConstants.PAY_TYPE_OFFLINE)) {
            throw new CrmebException("???????????????????????????");
        }
        response.setStatus(false);
        return response;
    }


    private UserIntegralRecord integralRecordSubInit(StoreOrder storeOrder, User user) {
        UserIntegralRecord integralRecord = new UserIntegralRecord();
        integralRecord.setUid(storeOrder.getUid());
        integralRecord.setLinkId(storeOrder.getOrderId());
        integralRecord.setLinkType(IntegralRecordConstants.INTEGRAL_RECORD_LINK_TYPE_ORDER);
        integralRecord.setType(IntegralRecordConstants.INTEGRAL_RECORD_TYPE_SUB);
        integralRecord.setTitle(IntegralRecordConstants.BROKERAGE_RECORD_TITLE_ORDER);
        integralRecord.setIntegral(storeOrder.getUseIntegral());
        integralRecord.setBalance(user.getIntegral());
        integralRecord.setMark(StrUtil.format("??????????????????{}??????????????????", storeOrder.getUseIntegral()));
        integralRecord.setStatus(IntegralRecordConstants.INTEGRAL_RECORD_STATUS_COMPLETE);
        return integralRecord;
    }

    private UserBill userBillInit(StoreOrder order, User user) {
        UserBill userBill = new UserBill();
        userBill.setPm(0);
        userBill.setUid(order.getUid());
        userBill.setLinkId(order.getId().toString());
        userBill.setTitle("????????????");
        userBill.setCategory(Constants.USER_BILL_CATEGORY_MONEY);
        userBill.setType(Constants.USER_BILL_TYPE_PAY_ORDER);
        userBill.setNumber(order.getPayPrice());
        userBill.setBalance(user.getNowMoney());
        userBill.setMark("??????" + order.getPayPrice() + "???????????????");
        return userBill;
    }

    /**
     * ??????????????????
     */
    private UserBill experienceBillInit(StoreOrder storeOrder, Integer balance, Integer experience) {
        UserBill userBill = new UserBill();
        userBill.setPm(1);
        userBill.setUid(storeOrder.getUid());
        userBill.setLinkId(storeOrder.getId().toString());
        userBill.setTitle(Constants.ORDER_LOG_MESSAGE_PAY_SUCCESS);
        userBill.setCategory(Constants.USER_BILL_CATEGORY_EXPERIENCE);
        userBill.setType(Constants.USER_BILL_TYPE_PAY_ORDER);
        userBill.setNumber(new BigDecimal(experience));
        userBill.setBalance(new BigDecimal(balance));
        userBill.setMark("????????????????????????" + experience + "??????");
        return userBill;
    }

    /**
     * ??????????????????
     * @return UserIntegralRecord
     */
    private UserIntegralRecord integralRecordInit(StoreOrder storeOrder, Integer balance, Integer integral, String type) {
        UserIntegralRecord integralRecord = new UserIntegralRecord();
        integralRecord.setUid(storeOrder.getUid());
        integralRecord.setLinkId(storeOrder.getOrderId());
        integralRecord.setLinkType(IntegralRecordConstants.INTEGRAL_RECORD_LINK_TYPE_ORDER);
        integralRecord.setType(IntegralRecordConstants.INTEGRAL_RECORD_TYPE_ADD);
        integralRecord.setTitle(IntegralRecordConstants.BROKERAGE_RECORD_TITLE_ORDER);
        integralRecord.setIntegral(integral);
        integralRecord.setBalance(balance);
        if (type.equals("order")){
            integralRecord.setMark(StrUtil.format("??????????????????,????????????{}??????", integral));
        }
        if (type.equals("product")) {
            integralRecord.setMark(StrUtil.format("??????????????????,????????????{}??????", integral));
        }
        integralRecord.setStatus(IntegralRecordConstants.INTEGRAL_RECORD_STATUS_CREATE);
        // ?????????????????????
        String fronzenTime = systemConfigService.getValueByKey(Constants.CONFIG_KEY_STORE_INTEGRAL_EXTRACT_TIME);
        integralRecord.setFrozenTime(Integer.valueOf(Optional.ofNullable(fronzenTime).orElse("0")));
        integralRecord.setCreateTime(DateUtil.nowDateTime());
        return integralRecord;
    }

    /**
     * ??????????????????
     * ????????????????????????
     * ?????????????????????
     * ?????????????????????
     */
    private void pushMessageOrder(StoreOrder storeOrder, User user) {
        if (storeOrder.getIsChannel().equals(2)) {// H5
            return;
        }
        UserToken userToken;
        HashMap<String, String> temMap = new HashMap<>();
        if (!storeOrder.getPayType().equals(Constants.PAY_TYPE_WE_CHAT)) {
            return;
        }
        // ?????????
        if (storeOrder.getIsChannel().equals(Constants.ORDER_PAY_CHANNEL_PUBLIC)) {
            userToken = userTokenService.getTokenByUserId(user.getUid(), UserConstants.USER_TOKEN_TYPE_WECHAT);
            if (ObjectUtil.isNull(userToken)) {
                return ;
            }
            // ????????????????????????
            temMap.put(Constants.WE_CHAT_TEMP_KEY_FIRST, "??????????????????????????????");
            temMap.put("keyword1", storeOrder.getOrderId());
            temMap.put("keyword2", storeOrder.getPayPrice().toString());
            temMap.put(Constants.WE_CHAT_TEMP_KEY_END, "?????????????????????");
            templateMessageService.pushTemplateMessage(Constants.WE_CHAT_TEMP_KEY_ORDER_PAY, temMap, userToken.getToken());
            return;
        }
        if (storeOrder.getIsChannel().equals(Constants.ORDER_PAY_CHANNEL_PROGRAM)) {
            // ???????????????????????????
            userToken = userTokenService.getTokenByUserId(user.getUid(), UserConstants.USER_TOKEN_TYPE_ROUTINE);
            if (ObjectUtil.isNull(userToken)) {
                return ;
            }
            // ????????????
            temMap.put("character_string1", storeOrder.getOrderId());
            temMap.put("amount2", storeOrder.getPayPrice().toString() + "???");
            temMap.put("thing7", "???????????????????????????");
            templateMessageService.pushMiniTemplateMessage(Constants.WE_CHAT_PROGRAM_TEMP_KEY_ORDER_PAY, temMap, userToken.getToken());
        }
    }

    /**
     * ?????????????????????????????????
     */
    private void autoSendCoupons(StoreOrder storeOrder){
        // ????????????????????????????????????
        List<StoreOrderInfoOldVo> orders = storeOrderInfoService.getOrderListByOrderId(storeOrder.getId());
        if(null == orders){
            return;
        }
        List<StoreCouponUser> couponUserList = CollUtil.newArrayList();
        Map<Integer, Boolean> couponMap = CollUtil.newHashMap();
        for (StoreOrderInfoOldVo order : orders) {
            List<StoreProductCoupon> couponsForGiveUser = storeProductCouponService.getListByProductId(order.getProductId());
            for (int i = 0; i < couponsForGiveUser.size();) {
                StoreProductCoupon storeProductCoupon = couponsForGiveUser.get(i);
                MyRecord record = storeCouponUserService.paySuccessGiveAway(storeProductCoupon.getIssueCouponId(), storeOrder.getUid());
                if (record.getStr("status").equals("fail")) {
                    logger.error(StrUtil.format("???????????????????????????????????????????????????{}", record.getStr("errMsg")));
                    couponsForGiveUser.remove(i);
                    continue;
                }

                StoreCouponUser storeCouponUser = record.get("storeCouponUser");
                couponUserList.add(storeCouponUser);
                couponMap.put(storeCouponUser.getCouponId(), record.getBoolean("isLimited"));
                i++;
            }
        }

        Boolean execute = transactionTemplate.execute(e -> {
            if (CollUtil.isNotEmpty(couponUserList)) {
                storeCouponUserService.saveBatch(couponUserList);
                couponUserList.forEach(i -> storeCouponService.deduction(i.getCouponId(), 1, couponMap.get(i.getCouponId())));
            }
            return Boolean.TRUE;
        });
        if (!execute) {
            logger.error(StrUtil.format("?????????????????????????????????????????????????????????????????????{}", storeOrder.getOrderId()));
        }
    }
}
