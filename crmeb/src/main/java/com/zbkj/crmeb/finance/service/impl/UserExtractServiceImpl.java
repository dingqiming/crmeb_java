package com.zbkj.crmeb.finance.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.CommonPage;
import com.common.PageParamRequest;
import com.constants.BrokerageRecordConstants;
import com.constants.Constants;
import com.exception.CrmebException;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.utils.DateUtil;
import com.utils.vo.dateLimitUtilVo;
import com.zbkj.crmeb.finance.dao.UserExtractDao;
import com.zbkj.crmeb.finance.model.UserExtract;
import com.zbkj.crmeb.finance.request.UserExtractRequest;
import com.zbkj.crmeb.finance.request.UserExtractSearchRequest;
import com.zbkj.crmeb.finance.response.BalanceResponse;
import com.zbkj.crmeb.finance.response.UserExtractResponse;
import com.zbkj.crmeb.finance.service.UserExtractService;
import com.zbkj.crmeb.front.response.UserExtractRecordResponse;
import com.zbkj.crmeb.system.service.SystemAttachmentService;
import com.zbkj.crmeb.system.service.SystemConfigService;
import com.zbkj.crmeb.user.model.User;
import com.zbkj.crmeb.user.model.UserBrokerageRecord;
import com.zbkj.crmeb.user.service.UserBillService;
import com.zbkj.crmeb.user.service.UserBrokerageRecordService;
import com.zbkj.crmeb.user.service.UserService;
import com.zbkj.crmeb.wechat.service.impl.WechatSendMessageForMinService;
import com.zbkj.crmeb.wechat.vo.WechatSendMessageForCash;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ZERO;

/**
*  UserExtractServiceImpl ????????????
*  +----------------------------------------------------------------------
 *  | CRMEB [ CRMEB???????????????????????????????????? ]
 *  +----------------------------------------------------------------------
 *  | Copyright (c) 2016~2020 https://www.crmeb.com All rights reserved.
 *  +----------------------------------------------------------------------
 *  | Licensed CRMEB????????????????????????????????????????????????CRMEB????????????
 *  +----------------------------------------------------------------------
 *  | Author: CRMEB Team <admin@crmeb.com>
 *  +----------------------------------------------------------------------
*/
@Service
public class UserExtractServiceImpl extends ServiceImpl<UserExtractDao, UserExtract> implements UserExtractService {

    @Resource
    private UserExtractDao dao;

    @Autowired
    private UserService userService;

    @Autowired
    private UserBillService userBillService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private WechatSendMessageForMinService wechatSendMessageForMinService;

    @Autowired
    private SystemAttachmentService systemAttachmentService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private UserBrokerageRecordService userBrokerageRecordService;


    /**
    * ??????
    * @param request ????????????
    * @param pageParamRequest ???????????????
    * @author Mr.Zhang
    * @since 2020-05-11
    * @return List<UserExtract>
    */
    @Override
    public List<UserExtract> getList(UserExtractSearchRequest request, PageParamRequest pageParamRequest) {
        PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());

        //??? UserExtract ?????????????????????
        LambdaQueryWrapper<UserExtract> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        if(!StringUtils.isBlank(request.getKeywords())){
            lambdaQueryWrapper.and(i -> i.
                    or().like(UserExtract::getWechat, request.getKeywords()).   //?????????
                    or().like(UserExtract::getRealName, request.getKeywords()). //??????
                    or().like(UserExtract::getBankCode, request.getKeywords()). //?????????
                    or().like(UserExtract::getBankAddress, request.getKeywords()). //?????????
                    or().like(UserExtract::getAlipayCode, request.getKeywords()). //?????????
                    or().like(UserExtract::getFailMsg, request.getKeywords()) //????????????
            );
        }

        //????????????
        if(request.getStatus() != null){
            lambdaQueryWrapper.eq(UserExtract::getStatus, request.getStatus());
        }

        //????????????
        if(!StringUtils.isBlank(request.getExtractType())){
            lambdaQueryWrapper.eq(UserExtract::getExtractType, request.getExtractType());
        }

        //????????????
        if(StringUtils.isNotBlank(request.getDateLimit())){
            dateLimitUtilVo dateLimit = DateUtil.getDateLimit(request.getDateLimit());
            lambdaQueryWrapper.between(UserExtract::getCreateTime, dateLimit.getStartTime(), dateLimit.getEndTime());
        }

        //???????????????????????????
        lambdaQueryWrapper.orderByDesc(UserExtract::getCreateTime, UserExtract::getId);

        List<UserExtract> extractList = dao.selectList(lambdaQueryWrapper);
        if (CollUtil.isEmpty(extractList)) {
            return extractList;
        }
        List<Integer> uidList = extractList.stream().map(o -> o.getUid()).distinct().collect(Collectors.toList());
        HashMap<Integer, User> userMap = userService.getMapListInUid(uidList);
        for (UserExtract userExtract : extractList) {
            userExtract.setNickName(Optional.ofNullable(userMap.get(userExtract.getUid()).getNickname()).orElse(""));
        }
        return extractList;
    }

    /**
     * ???????????????
     * ????????? = ??????????????? + ???????????????
     * ??????????????? = ???????????????????????????
     * ??????????????? = ???????????????????????? = ??????????????? + ???????????? = ????????????
     * ??????????????? = ????????????????????????????????????????????? = ???????????? - ???????????????
     * ??????????????? = ????????????????????????
     * ???????????? = ???????????????????????????????????????????????????
     * ???????????? = ????????????????????????????????????
     */
    @Override
    public BalanceResponse getBalance(String dateLimit) {
        String startTime = "";
        String endTime = "";
        if(StringUtils.isNotBlank(dateLimit)){
            dateLimitUtilVo dateRage = DateUtil.getDateLimit(dateLimit);
            startTime = dateRage.getStartTime();
            endTime = dateRage.getEndTime();
        }

        // ?????????
        BigDecimal withdrawn = getWithdrawn(startTime, endTime);
        // ?????????(?????????)
        BigDecimal toBeWithdrawn = getWithdrawning(startTime, endTime);

        // ?????????????????????????????????
        BigDecimal commissionTotal = userBrokerageRecordService.getTotalSpreadPriceBydateLimit(dateLimit);
        // ???????????????????????????
        BigDecimal subWithdarw = userBrokerageRecordService.getSubSpreadPriceByDateLimit(dateLimit);
        // ?????????
        BigDecimal unDrawn = commissionTotal.subtract(subWithdarw);
        return new BalanceResponse(withdrawn, unDrawn, commissionTotal, toBeWithdrawn);
    }


    /**
     * ???????????????
     * @author Mr.Zhang
     * @since 2020-05-11
     * @return BalanceResponse
     */
    @Override
    public BigDecimal getWithdrawn(String startTime, String endTime) {
        return getSum(null, 1, startTime, endTime);
    }

    /**
     * ??????????????????
     * @author Mr.Zhang
     * @since 2020-05-11
     * @return BalanceResponse
     */
    @Override
    public BigDecimal getWithdrawning(String startTime, String endTime) {
        return getSum(null, 0, startTime, endTime);
    }

     /**
     * ????????????
     * @author Mr.Zhang
     * @since 2020-06-08
     * @return Boolean
     */
    @Override
    public Boolean create(UserExtractRequest request, Integer userId) {
        //???????????????????????????????????????10???
        BigDecimal ten = new BigDecimal(10);
        if (request.getExtractPrice().compareTo(ten) < 0) {
            throw new CrmebException("??????????????????10???");
        }
        //????????????????????????????????????
        User user = userService.getById(userId);
        BigDecimal toBeWithdrawn = user.getBrokeragePrice();//???????????????
        BigDecimal freeze = getFreeze(userId); //???????????????
        BigDecimal money = toBeWithdrawn.subtract(freeze); //??????????????????

        if(money.compareTo(ZERO) < 1){
            throw new CrmebException("?????????????????????????????????");
        }

        int result = money.compareTo(request.getExtractPrice());
        if(result < 0){
            throw new CrmebException("???????????????????????? " + toBeWithdrawn + "???");
        }
        UserExtract userExtract = new UserExtract();
        userExtract.setUid(userId);
        BeanUtils.copyProperties(request, userExtract);
        userExtract.setBalance(toBeWithdrawn.subtract(request.getExtractPrice()));
        //??????????????????
//        userExtract.setBankName(request.getBankName());
        if (StrUtil.isNotBlank(userExtract.getQrcodeUrl())) {
            userExtract.setQrcodeUrl(systemAttachmentService.clearPrefix(userExtract.getQrcodeUrl()));
        }

        // ?????????????????????????????????
        WechatSendMessageForCash cash = new WechatSendMessageForCash(
                "??????????????????",request.getExtractPrice()+"",request.getBankName()+request.getBankCode(),
                DateUtil.nowDateTimeStr(),"??????",request.getRealName(),"0",request.getExtractType(),"??????",
                "??????",request.getExtractType(),"??????",request.getRealName()
        );
        wechatSendMessageForMinService.sendCashMessage(cash,userId);
        save(userExtract);
        // ?????????????????????
        return userService.updateBrokeragePrice(user, toBeWithdrawn.subtract(request.getExtractPrice()));
    }


    /**
     * ???????????????
     * @author Mr.Zhang
     * @since 2020-06-08
     * @return Boolean
     */
    @Override
    public BigDecimal getFreeze(Integer userId) {
        String time = systemConfigService.getValueByKey(Constants.CONFIG_KEY_STORE_BROKERAGE_EXTRACT_TIME);
        if (StrUtil.isBlank(time)) {
            return BigDecimal.ZERO;
        }
        String endTime = DateUtil.nowDateTime(Constants.DATE_FORMAT);
        String startTime = DateUtil.addDay(DateUtil.nowDateTime(), -Integer.parseInt(time), Constants.DATE_FORMAT);
        String date = startTime + "," + endTime;
        //?????????????????????
        BigDecimal getSum = userBillService.getSumBigDecimal(1, userId, Constants.USER_BILL_CATEGORY_BROKERAGE_PRICE, date, null);
        return getSum;
    }

    /**
     * ????????????????????????
     * @return BigDecimal
     */
    private BigDecimal getSum(Integer userId, int status, String startTime, String endTime) {
        LambdaQueryWrapper<UserExtract> lqw = Wrappers.lambdaQuery();
        if(null != userId) {
            lqw.eq(UserExtract::getUid,userId);
        }
        lqw.eq(UserExtract::getStatus,status);
        if(StringUtils.isNotBlank(startTime) && StringUtils.isNotBlank(endTime)){
            lqw.between(UserExtract::getCreateTime, startTime, endTime);
        }
        List<UserExtract> userExtracts = dao.selectList(lqw);
        BigDecimal sum = ZERO;
        if(CollUtil.isNotEmpty(userExtracts)) {
            sum = userExtracts.stream().map(UserExtract::getExtractPrice).reduce(ZERO, BigDecimal::add);
        }
        return sum;
    }

    /**
     * ?????????????????????????????????
     * @param userId ??????id
     * @return ????????????
     */
    @Override
    public UserExtractResponse getUserExtractByUserId(Integer userId) {
        QueryWrapper<UserExtract> qw = new QueryWrapper<>();
        qw.select("SUM(extract_price) as extract_price,count(id) as id, uid");
        qw.ge("status", 1);
        qw.eq("uid",userId);
        qw.groupBy("uid");
        UserExtract ux = dao.selectOne(qw);
        UserExtractResponse uexr = new UserExtractResponse();
//        uexr.setEuid(ux.getUid());
        if(null != ux){
            uexr.setExtractCountNum(ux.getId()); // ?????????id?????????????????????????????????
            uexr.setExtractCountPrice(ux.getExtractPrice());
        }else{
            uexr.setExtractCountNum(0); // ?????????id?????????????????????????????????
            uexr.setExtractCountPrice(ZERO);
        }

        return uexr;
    }

    /**
     * ????????????id????????????????????????????????????
     * @param userIds ??????id??????
     * @return ??????????????????
     */
    @Override
    public List<UserExtract> getListByUserIds(List<Integer> userIds) {
        LambdaQueryWrapper<UserExtract> lqw = new LambdaQueryWrapper<>();
        lqw.in(UserExtract::getUid, userIds);
        return dao.selectList(lqw);
    }

    /**
     * ????????????
     *
     * @param id          ????????????id
     * @param status      ???????????? -1 ????????? 0 ????????? 1 ?????????
     * @param backMessage ????????????
     * @return ????????????
     */
    @Override
    public Boolean updateStatus(Integer id, Integer status, String backMessage) {
        if(status == -1 && StringUtils.isBlank(backMessage))
            throw new CrmebException("??????????????????????????????");

        UserExtract userExtract = getById(id);
        if (ObjectUtil.isNull(userExtract)) {
            throw new CrmebException("???????????????????????????");
        }
        if (userExtract.getStatus() != 0) {
            throw new CrmebException("????????????????????????");
        }
        userExtract.setStatus(status);

        User user = userService.getById(userExtract.getUid());
        if (ObjectUtil.isNull(user)) {
            throw new CrmebException("????????????????????????");
        }

        Boolean execute = false;

        userExtract.setUpdateTime(cn.hutool.core.date.DateUtil.date());
        // ??????
        if (status == -1) {//?????????????????????????????????
            userExtract.setFailMsg(backMessage);
            // ????????????????????????????????????
            UserBrokerageRecord brokerageRecord = new UserBrokerageRecord();
            brokerageRecord.setUid(user.getUid());
            brokerageRecord.setLinkId(userExtract.getId().toString());
            brokerageRecord.setLinkType(BrokerageRecordConstants.BROKERAGE_RECORD_LINK_TYPE_WITHDRAW);
            brokerageRecord.setType(BrokerageRecordConstants.BROKERAGE_RECORD_TYPE_ADD);
            brokerageRecord.setTitle(BrokerageRecordConstants.BROKERAGE_RECORD_TITLE_WITHDRAW_FAIL);
            brokerageRecord.setPrice(userExtract.getExtractPrice());
            brokerageRecord.setBalance(user.getBrokeragePrice().add(userExtract.getExtractPrice()));
            brokerageRecord.setMark(StrUtil.format("??????????????????????????????{}", userExtract.getExtractPrice()));
            brokerageRecord.setStatus(BrokerageRecordConstants.BROKERAGE_RECORD_STATUS_COMPLETE);
            brokerageRecord.setCreateTime(DateUtil.nowDateTime());

            execute = transactionTemplate.execute(e -> {
                // ????????????
                userService.operationBrokerage(userExtract.getUid(), userExtract.getExtractPrice(), user.getBrokeragePrice(), "add");
                updateById(userExtract);
                userBrokerageRecordService.save(brokerageRecord);
                return Boolean.TRUE;
            });
        }

        // ??????
        if (status == 1) {
            // ????????????????????????
            UserBrokerageRecord brokerageRecord = userBrokerageRecordService.getByLinkIdAndLinkType(userExtract.getId().toString(), BrokerageRecordConstants.BROKERAGE_RECORD_LINK_TYPE_WITHDRAW);
            if (ObjectUtil.isNull(brokerageRecord)) {
                throw new CrmebException("??????????????????????????????");
            }
            execute = transactionTemplate.execute(e -> {
                updateById(userExtract);
                brokerageRecord.setStatus(BrokerageRecordConstants.BROKERAGE_RECORD_STATUS_COMPLETE);
                userBrokerageRecordService.updateById(brokerageRecord);
                return Boolean.TRUE;
            });
        }
        return execute;
    }

    /**
     * ????????????
     * @return
     */
    @Override
    public PageInfo<UserExtractRecordResponse> getExtractRecord(Integer userId, PageParamRequest pageParamRequest){
        Page<UserExtract> userExtractPage = PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());
        QueryWrapper<UserExtract> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("uid", userId);

        queryWrapper.groupBy("left(create_time, 7)");
        queryWrapper.orderByDesc("left(create_time, 7)");
        List<UserExtract> list = dao.selectList(queryWrapper);
        if(CollUtil.isEmpty(list)){
            return new PageInfo<>();
        }
        ArrayList<UserExtractRecordResponse> userExtractRecordResponseList = CollectionUtil.newArrayList();
        for (UserExtract userExtract : list) {
            String date = DateUtil.dateToStr(userExtract.getCreateTime(), Constants.DATE_FORMAT_MONTH);
            userExtractRecordResponseList.add(new UserExtractRecordResponse(date, getListByMonth(userId, date)));
        }

        return CommonPage.copyPageInfo(userExtractPage, userExtractRecordResponseList);
    }

    private List<UserExtract> getListByMonth(Integer userId, String date) {
        QueryWrapper<UserExtract> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "extract_price", "status", "create_time", "update_time");
        queryWrapper.eq("uid", userId);
        queryWrapper.apply(StrUtil.format(" left(create_time, 7) = '{}'", date));
        queryWrapper.orderByDesc("create_time");
        return dao.selectList(queryWrapper);
    }

    /**
     * ???????????????????????????
     * @param userId
     * @return
     */
    @Override
    public BigDecimal getExtractTotalMoney(Integer userId){
        return getSum(userId, 1, null, null);
    }


    /**
     * ????????????
     * @return
     */
    @Override
    public Boolean extractApply(UserExtractRequest request) {
        //???????????????????????????????????????????????????
        String value = systemConfigService.getValueByKeyException(Constants.CONFIG_EXTRACT_MIN_PRICE);
        BigDecimal ten = new BigDecimal(value);
        if (request.getExtractPrice().compareTo(ten) < 0) {
            throw new CrmebException(StrUtil.format("??????????????????{}???", ten));
        }

        User user = userService.getInfo();
        if (ObjectUtil.isNull(user)) {
            throw new CrmebException("????????????????????????");
        }
        BigDecimal money = user.getBrokeragePrice();//??????????????????
        if(money.compareTo(ZERO) < 1){
            throw new CrmebException("?????????????????????????????????");
        }

        if(money.compareTo(request.getExtractPrice()) < 0){
            throw new CrmebException("???????????????????????? " + money + "???");
        }

        UserExtract userExtract = new UserExtract();
        BeanUtils.copyProperties(request, userExtract);
        userExtract.setUid(user.getUid());
        userExtract.setBalance(money.subtract(request.getExtractPrice()));
        //??????????????????
        if (StrUtil.isNotBlank(userExtract.getQrcodeUrl())) {
            userExtract.setQrcodeUrl(systemAttachmentService.clearPrefix(userExtract.getQrcodeUrl()));
        }

        // ??????????????????
        UserBrokerageRecord brokerageRecord = new UserBrokerageRecord();
        brokerageRecord.setUid(user.getUid());
        brokerageRecord.setLinkType(BrokerageRecordConstants.BROKERAGE_RECORD_LINK_TYPE_WITHDRAW);
        brokerageRecord.setType(BrokerageRecordConstants.BROKERAGE_RECORD_TYPE_SUB);
        brokerageRecord.setTitle(BrokerageRecordConstants.BROKERAGE_RECORD_TITLE_WITHDRAW_APPLY);
        brokerageRecord.setPrice(userExtract.getExtractPrice());
        brokerageRecord.setBalance(money.subtract(userExtract.getExtractPrice()));
        brokerageRecord.setMark(StrUtil.format("????????????????????????{}", userExtract.getExtractPrice()));
        brokerageRecord.setStatus(BrokerageRecordConstants.BROKERAGE_RECORD_STATUS_WITHDRAW);
        brokerageRecord.setCreateTime(DateUtil.nowDateTime());

        Boolean execute = transactionTemplate.execute(e -> {
            // ??????????????????
            save(userExtract);
            // ??????????????????
            userService.operationBrokerage(user.getUid(), userExtract.getExtractPrice(), money, "sub");
            // ??????????????????
            brokerageRecord.setLinkId(userExtract.getId().toString());
            userBrokerageRecordService.save(brokerageRecord);
            return Boolean.TRUE;
        });

        //todo ??????????????????
        return execute;
    }
}

