package com.zbkj.crmeb.user.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.CommonPage;
import com.common.PageParamRequest;
import com.constants.*;
import com.exception.CrmebException;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.utils.*;
import com.utils.vo.dateLimitUtilVo;
import com.zbkj.crmeb.authorization.manager.TokenManager;
import com.zbkj.crmeb.authorization.model.TokenModel;
import com.zbkj.crmeb.finance.request.FundsMonitorSearchRequest;
import com.zbkj.crmeb.front.request.PasswordRequest;
import com.zbkj.crmeb.front.request.UserBindingPhoneUpdateRequest;
import com.zbkj.crmeb.front.response.UserCenterResponse;
import com.zbkj.crmeb.front.response.UserSpreadPeopleItemResponse;
import com.zbkj.crmeb.front.service.LoginService;
import com.zbkj.crmeb.marketing.model.StoreCoupon;
import com.zbkj.crmeb.marketing.model.StoreCouponUser;
import com.zbkj.crmeb.marketing.request.StoreCouponUserSearchRequest;
import com.zbkj.crmeb.marketing.service.StoreCouponService;
import com.zbkj.crmeb.marketing.service.StoreCouponUserService;
import com.zbkj.crmeb.store.model.StoreOrder;
import com.zbkj.crmeb.store.request.RetailShopStairUserRequest;
import com.zbkj.crmeb.store.request.StoreOrderSearchRequest;
import com.zbkj.crmeb.store.response.SpreadOrderResponse;
import com.zbkj.crmeb.store.service.StoreOrderService;
import com.zbkj.crmeb.store.service.StoreProductRelationService;
import com.zbkj.crmeb.system.model.SystemUserLevel;
import com.zbkj.crmeb.system.service.SystemConfigService;
import com.zbkj.crmeb.system.service.SystemUserLevelService;
import com.zbkj.crmeb.user.dao.UserDao;
import com.zbkj.crmeb.user.model.*;
import com.zbkj.crmeb.user.request.*;
import com.zbkj.crmeb.user.response.TopDetail;
import com.zbkj.crmeb.user.response.UserResponse;
import com.zbkj.crmeb.user.service.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ????????? ???????????????
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
@Service
public class UserServiceImpl extends ServiceImpl<UserDao, User> implements UserService {

    @Resource
    private UserDao userDao;

    @Autowired
    private UserBillService userBillService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private TokenManager tokenManager;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private SystemUserLevelService systemUserLevelService;

    @Autowired
    private UserLevelService userLevelService;

    @Autowired
    private UserTagService userTagService;

    @Autowired
    private UserGroupService userGroupService;

    @Autowired
    private StoreOrderService storeOrderService;

    @Autowired
    private UserSignService userSignService;

    @Autowired
    private StoreCouponUserService storeCouponUserService;

    @Autowired
    private StoreCouponService storeCouponService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private UserIntegralRecordService userIntegralRecordService;

    @Autowired
    private UserBrokerageRecordService userBrokerageRecordService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private StoreProductRelationService storeProductRelationService;

    /**
     * ?????????????????????
     *
     * @param request          ????????????
     * @param pageParamRequest ????????????
     */
    @Override
    public PageInfo<UserResponse> getList(UserSearchRequest request, PageParamRequest pageParamRequest) {
        Page<User> pageUser = PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        Map<String, Object> map = CollUtil.newHashMap();

        if (request.getIsPromoter() != null) {
            map.put("isPromoter", request.getIsPromoter() ? 1 : 0);
        }

        if (!StringUtils.isBlank(request.getGroupId())) {
            map.put("groupId", request.getGroupId());
        }

        if (!StringUtils.isBlank(request.getLabelId())) {
            String tagIdSql = CrmebUtil.getFindInSetSql("u.tag_id", request.getLabelId());
            map.put("tagIdSql", tagIdSql);
        }

        if (!StringUtils.isBlank(request.getLevel())) {
            map.put("level", request.getLevel());
        }

        if (StringUtils.isNotBlank(request.getUserType())) {
            map.put("userType", request.getUserType());
        }

        if (StringUtils.isNotBlank(request.getSex())) {
            lambdaQueryWrapper.eq(User::getSex, request.getSex());
            map.put("sex", Integer.valueOf(request.getSex()));
        }

        if (StringUtils.isNotBlank(request.getCountry())) {
            map.put("country", request.getCountry());
            // ??????????????????
            if (StrUtil.isNotBlank(request.getCity())) {
                request.setProvince(request.getProvince().replace("???", ""));
                request.setCity(request.getCity().replace("???", ""));
                map.put("addres", request.getProvince() + "," + request.getCity());
            }
        }

        if (StrUtil.isNotBlank(request.getPayCount())) {
            map.put("payCount", Integer.valueOf(request.getPayCount()));
        }

        if (request.getStatus() != null) {
            map.put("status", request.getStatus() ? 1 : 0);
        }

        dateLimitUtilVo dateLimit = DateUtil.getDateLimit(request.getDateLimit());

        if (!StringUtils.isBlank(dateLimit.getStartTime())) {
            map.put("startTime", dateLimit.getStartTime());
            map.put("endTime", dateLimit.getEndTime());
            map.put("accessType", request.getAccessType());
        }
        if (request.getKeywords() != null) {
            map.put("keywords", request.getKeywords());
        }
        List<User> userList = userDao.findAdminList(map);
        List<UserResponse> userResponses = new ArrayList<>();
        for (User user : userList) {
            UserResponse userResponse = new UserResponse();
            BeanUtils.copyProperties(user, userResponse);
            // ??????????????????
            if (!StringUtils.isBlank(user.getGroupId())) {
                userResponse.setGroupName(userGroupService.getGroupNameInId(user.getGroupId()));
                userResponse.setGroupId(user.getGroupId());
            }

            // ??????????????????
            if (!StringUtils.isBlank(user.getTagId())) {
                userResponse.setTagName(userTagService.getGroupNameInId(user.getTagId()));
                userResponse.setTagId(user.getTagId());
            }

            //?????????????????????
            if (null == user.getSpreadUid() || user.getSpreadUid() == 0) {
                userResponse.setSpreadNickname("???");
            } else {
                userResponse.setSpreadNickname(userDao.selectById(user.getSpreadUid()).getNickname());
            }
            userResponses.add(userResponse);
        }
        return CommonPage.copyPageInfo(pageUser, userResponses);
    }

    /**
     * ?????????????????????
     */
    @Override
    public boolean updateIntegralMoney(UserOperateIntegralMoneyRequest request) {
        if (null == request.getMoneyValue() || null == request.getIntegralValue()) {
            throw new CrmebException("????????????????????????");
        }

        if (request.getMoneyValue().compareTo(BigDecimal.ZERO) < 1 && request.getIntegralValue() <= 0) {
            throw new CrmebException("??????????????????????????????0");
        }

        User user = getById(request.getUid());
        if (ObjectUtil.isNull(user)) {
            throw new CrmebException("???????????????");
        }
        // ????????????????????????0?????????,???????????????????????????????????????
        if (request.getMoneyType().equals(2) && request.getMoneyValue().compareTo(BigDecimal.ZERO) != 0) {
            if (user.getNowMoney().subtract(request.getMoneyValue()).compareTo(BigDecimal.ZERO) < 0) {
                throw new CrmebException("???????????????????????????0");
            }
        }
        if (request.getMoneyType().equals(1) && request.getMoneyValue().compareTo(BigDecimal.ZERO) != 0) {
            if (user.getNowMoney().add(request.getMoneyValue()).compareTo(new BigDecimal("99999999.99")) > 0) {
                throw new CrmebException("??????????????????????????????99999999.99");
            }
        }

        if (request.getIntegralType().equals(2) && request.getIntegralValue() != 0) {
            if (user.getIntegral() - request.getIntegralValue() < 0) {
                throw new CrmebException("???????????????????????????0");
            }
        }
        if (request.getIntegralType().equals(1) && request.getIntegralValue() != 0) {
            if ((user.getIntegral() + request.getIntegralValue()) > 99999999) {
                throw new CrmebException("???????????????????????????99999999");
            }
        }

        Boolean execute = transactionTemplate.execute(e -> {
            // ????????????
            if (request.getMoneyValue().compareTo(BigDecimal.ZERO) > 0) {
                // ??????UserBill
                UserBill userBill = new UserBill();
                userBill.setUid(user.getUid());
                userBill.setLinkId("0");
                userBill.setTitle("????????????");
                userBill.setCategory(Constants.USER_BILL_CATEGORY_MONEY);
                userBill.setNumber(request.getMoneyValue());
                userBill.setStatus(1);
                userBill.setCreateTime(DateUtil.nowDateTime());

                if (request.getMoneyType() == 1) {// ??????
                    userBill.setPm(1);
                    userBill.setType(Constants.USER_BILL_TYPE_SYSTEM_ADD);
                    userBill.setBalance(user.getNowMoney().add(request.getMoneyValue()));
                    userBill.setMark(StrUtil.format("?????????????????????{}??????", request.getMoneyValue()));

                    userBillService.save(userBill);
                    operationNowMoney(user.getUid(), request.getMoneyValue(), user.getNowMoney(), "add");
                } else {
                    userBill.setPm(0);
                    userBill.setType(Constants.USER_BILL_TYPE_SYSTEM_SUB);
                    userBill.setBalance(user.getNowMoney().subtract(request.getMoneyValue()));
                    userBill.setMark(StrUtil.format("?????????????????????{}??????", request.getMoneyValue()));

                    userBillService.save(userBill);
                    operationNowMoney(user.getUid(), request.getMoneyValue(), user.getNowMoney(), "sub");
                }
            }

            // ????????????
            if (request.getIntegralValue() > 0) {
                // ????????????
                UserIntegralRecord integralRecord = new UserIntegralRecord();
                integralRecord.setUid(user.getUid());
                integralRecord.setLinkType(IntegralRecordConstants.INTEGRAL_RECORD_LINK_TYPE_SIGN);
                integralRecord.setTitle(IntegralRecordConstants.BROKERAGE_RECORD_TITLE_SYSTEM);
                integralRecord.setIntegral(request.getIntegralValue());
                integralRecord.setStatus(IntegralRecordConstants.INTEGRAL_RECORD_STATUS_COMPLETE);
                if (request.getIntegralType() == 1) {// ??????
                    integralRecord.setType(IntegralRecordConstants.INTEGRAL_RECORD_TYPE_ADD);
                    integralRecord.setBalance(user.getIntegral() + request.getIntegralValue());
                    integralRecord.setMark(StrUtil.format("?????????????????????{}??????", request.getIntegralValue()));

                    operationIntegral(user.getUid(), request.getIntegralValue(), user.getIntegral(), "add");
                } else {
                    integralRecord.setType(IntegralRecordConstants.INTEGRAL_RECORD_TYPE_SUB);
                    integralRecord.setBalance(user.getIntegral() - request.getIntegralValue());
                    integralRecord.setMark(StrUtil.format("?????????????????????{}??????", request.getIntegralValue()));
                    operationIntegral(user.getUid(), request.getIntegralValue(), user.getIntegral(), "sub");
                }
                userIntegralRecordService.save(integralRecord);
            }
            return Boolean.TRUE;
        });

        if (!execute) {
            throw new CrmebException("????????????/????????????");
        }
        return execute;
    }

    /**
     * ??????????????????
     *
     * @param user ????????????
     * @return ????????????
     */
    @Override
    public boolean updateBase(User user) {
        LambdaUpdateWrapper<User> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        if (null == user.getUid()) return false;
        lambdaUpdateWrapper.eq(User::getUid, user.getUid());
        if (StringUtils.isNotBlank(user.getNickname())) {
            lambdaUpdateWrapper.set(User::getNickname, user.getNickname());
        }
        if (StringUtils.isNotBlank(user.getAccount())) {
            lambdaUpdateWrapper.set(User::getAccount, user.getAccount());
        }
        if (StringUtils.isNotBlank(user.getPwd())) {
            lambdaUpdateWrapper.set(User::getPwd, user.getPwd());
        }
        if (StringUtils.isNotBlank(user.getRealName())) {
            lambdaUpdateWrapper.set(User::getRealName, user.getRealName());
        }
        if (StringUtils.isNotBlank(user.getBirthday())) {
            lambdaUpdateWrapper.set(User::getBirthday, user.getBirthday());
        }
        if (StringUtils.isNotBlank(user.getCardId())) {
            lambdaUpdateWrapper.set(User::getCardId, user.getCardId());
        }
        if (StringUtils.isNotBlank(user.getMark())) {
            lambdaUpdateWrapper.set(User::getMark, user.getMark());
        }
        if (null != user.getPartnerId()) {
            lambdaUpdateWrapper.set(User::getPartnerId, user.getPartnerId());
        }
        if (StringUtils.isNotBlank(user.getGroupId())) {
            lambdaUpdateWrapper.set(User::getGroupId, user.getGroupId());
        }
        if (StringUtils.isNotBlank(user.getTagId())) {
            lambdaUpdateWrapper.set(User::getTagId, user.getTagId());
        }
        if (StringUtils.isNotBlank(user.getAvatar())) {
            lambdaUpdateWrapper.set(User::getAvatar, user.getAvatar());
        }
        if (StringUtils.isNotBlank(user.getPhone())) {
            lambdaUpdateWrapper.set(User::getPhone, user.getPhone());
        }
        if (StringUtils.isNotBlank(user.getAddIp())) {
            lambdaUpdateWrapper.set(User::getAddIp, user.getAddIp());
        }
        if (StringUtils.isNotBlank(user.getLastIp())) {
            lambdaUpdateWrapper.set(User::getLastIp, user.getLastIp());
        }
        if (null != user.getNowMoney() && user.getNowMoney().compareTo(BigDecimal.ZERO) > 0) {
            lambdaUpdateWrapper.set(User::getNowMoney, user.getNowMoney());
        }
        if (null != user.getBrokeragePrice() && user.getBrokeragePrice().compareTo(BigDecimal.ZERO) > 0) {
            lambdaUpdateWrapper.set(User::getBrokeragePrice, user.getBrokeragePrice());
        }
        if (null != user.getIntegral() && user.getIntegral() >= 0) {
            lambdaUpdateWrapper.set(User::getIntegral, user.getIntegral());
        }
        if (null != user.getExperience() && user.getExperience() > 0) {
            lambdaUpdateWrapper.set(User::getExperience, user.getExperience());
        }
        if (null != user.getSignNum() && user.getSignNum() > 0) {
            lambdaUpdateWrapper.set(User::getSignNum, user.getSignNum());
        }
        if (null != user.getStatus()) {
            lambdaUpdateWrapper.set(User::getStatus, user.getStatus());
        }
        if (null != user.getLevel() && user.getLevel() > 0) {
            lambdaUpdateWrapper.set(User::getLevel, user.getLevel());
        }
        if (null != user.getSpreadUid() && user.getSpreadUid() > 0) {
            lambdaUpdateWrapper.set(User::getSpreadUid, user.getSpreadUid());
        }
        if (null != user.getSpreadTime()) {
            lambdaUpdateWrapper.set(User::getSpreadTime, user.getSpreadTime());
        }
        if (StringUtils.isNotBlank(user.getUserType())) {
            lambdaUpdateWrapper.set(User::getUserType, user.getUserType());
        }
        if (null != user.getIsPromoter()) {
            lambdaUpdateWrapper.set(User::getIsPromoter, user.getIsPromoter());
        }
        if (null != user.getPayCount()) {
            lambdaUpdateWrapper.set(User::getPayCount, user.getPayCount());
        }
        if (null != user.getSpreadCount()) {
            lambdaUpdateWrapper.set(User::getSpreadCount, user.getSpreadCount());
        }
        if (StringUtils.isNotBlank(user.getAddres())) {
            lambdaUpdateWrapper.set(User::getAddres, user.getAddres());
        }
        return update(lambdaUpdateWrapper);
    }

    @Override
    public boolean userPayCountPlus(User user) {
        LambdaUpdateWrapper<User> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper.eq(User::getUid, user.getUid());
        lambdaUpdateWrapper.set(User::getPayCount, user.getPayCount() + 1);
        return update(lambdaUpdateWrapper);
    }

    /**
     * ??????????????????
     *
     * @param user  ??????
     * @param price ??????
     * @param type  ??????add?????????sub
     * @return ????????????????????????
     */
    @Override
    public Boolean updateNowMoney(User user, BigDecimal price, String type) {
        LambdaUpdateWrapper<User> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
        if (type.equals("add")) {
            lambdaUpdateWrapper.set(User::getNowMoney, user.getNowMoney().add(price));
        } else {
            lambdaUpdateWrapper.set(User::getNowMoney, user.getNowMoney().subtract(price));
        }
        lambdaUpdateWrapper.eq(User::getUid, user.getUid());
        if (type.equals("sub")) {
            lambdaUpdateWrapper.apply(StrUtil.format(" now_money - {} >= 0", price));
        }
        return update(lambdaUpdateWrapper);
    }

    /**
     * ????????????
     *
     * @param id           String id
     * @param groupIdValue Integer ??????Id
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    @Override
    public boolean group(String id, String groupIdValue) {
        if (StrUtil.isBlank(id)) throw new CrmebException("????????????????????????");
        if (StrUtil.isBlank(groupIdValue)) throw new CrmebException("??????id????????????");

        //??????id??????
        List<Integer> idList = CrmebUtil.stringToArray(id);
        idList = idList.stream().distinct().collect(Collectors.toList());
        List<User> list = getListInUid(idList);
        if (CollUtil.isEmpty(list)) throw new CrmebException("????????????????????????");
        if (list.size() < idList.size()) {
            throw new CrmebException("????????????????????????");
        }
        for (User user : list) {
            user.setGroupId(groupIdValue);
        }
        return updateBatchById(list);
    }

    /**
     * ??????id in list
     *
     * @param uidList List<Integer> id
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    private List<User> getListInUid(List<Integer> uidList) {
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.in(User::getUid, uidList);
        return userDao.selectList(lambdaQueryWrapper);
    }

    /**
     * ????????????
     *
     * @param request PasswordRequest ??????
     * @return boolean
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    @Override
    public boolean password(PasswordRequest request) {
        //???????????????
        checkValidateCode(request.getPhone(), request.getValidateCode());

        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getAccount, request.getPhone());
        User user = userDao.selectOne(lambdaQueryWrapper);

        //??????
        user.setPwd(CrmebUtil.encryptPassword(request.getPassword(), user.getAccount()));
        return update(user, lambdaQueryWrapper);
    }

    /**
     * ??????
     *
     * @param token String token
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    @Override
    public void loginOut(String token) {
        tokenManager.deleteToken(token, Constants.USER_TOKEN_REDIS_KEY_PREFIX);
        ThreadLocalUtil.remove("id");
    }

    /**
     * ??????????????????
     *
     * @return User
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    @Override
    public User getInfo() {
        if (getUserId() == 0) {
            return null;
        }
        return getById(getUserId());
    }

    /**
     * ??????????????????
     *
     * @return User
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    @Override
    public User getInfoException() {
        User user = getInfo();
        if (user == null) {
            throw new CrmebException("????????????????????????");
        }

        if (!user.getStatus()) {
            throw new CrmebException("????????????????????????");
        }
        return user;
    }

    /**
     * ??????????????????id
     *
     * @return Integer
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    @Override
    public Integer getUserIdException() {
        return Integer.parseInt(tokenManager.getLocalInfoException("id"));
    }

    /**
     * ??????????????????id
     *
     * @return Integer
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    @Override
    public Integer getUserId() {
        Object id = tokenManager.getLocalInfo("id");
        if (null == id) {
            return 0;
        }
        return Integer.parseInt(id.toString());
    }

    /**
     * ?????????????????????????????????????????????
     *
     * @param date String ????????????
     * @return HashMap<String, Object>
     * @author Mr.Zhang
     * @since 2020-05-16
     */
    @Override
    public Integer getAddUserCountByDate(String date) {
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(date)) {
            dateLimitUtilVo dateLimit = DateUtil.getDateLimit(date);
            lambdaQueryWrapper.between(User::getCreateTime, dateLimit.getStartTime(), dateLimit.getEndTime());
        }
        return userDao.selectCount(lambdaQueryWrapper);
    }

    /**
     * ???????????????????????????????????????????????????
     *
     * @param date String ????????????
     * @return HashMap<String, Object>
     */
    @Override
    public Map<Object, Object> getAddUserCountGroupDate(String date) {
        Map<Object, Object> map = new HashMap<>();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("count(uid) as uid", "left(create_time, 10) as create_time");
        if (StringUtils.isNotBlank(date)) {
            dateLimitUtilVo dateLimit = DateUtil.getDateLimit(date);
            queryWrapper.between("create_time", dateLimit.getStartTime(), dateLimit.getEndTime());
        }
        queryWrapper.groupBy("left(create_time, 10)").orderByAsc("create_time");
        List<User> list = userDao.selectList(queryWrapper);
        if (list.size() < 1) {
            return map;
        }

        for (User user : list) {
            map.put(DateUtil.dateToStr(user.getCreateTime(), Constants.DATE_FORMAT_DATE), user.getUid());
        }
        return map;
    }

    /**
     * ???????????????
     *
     * @return boolean
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    @Override
    public boolean bind(UserBindingPhoneUpdateRequest request) {
        //???????????????
        checkValidateCode(request.getPhone(), request.getCaptcha());

        //???????????????
        redisUtil.remove(getValidateCodeRedisKey(request.getPhone()));

        //??????????????????????????????????????????
        User user = getUserByAccount(request.getPhone());
        if (null != user) {
            throw new CrmebException("???????????????????????????");
        }

        //?????????????????????
        User bindUser = getInfoException();
        bindUser.setAccount(request.getPhone());
        bindUser.setPhone(request.getPhone());

        return updateById(bindUser);
    }

    /**
     * ?????????????????????
     */
    @Override
    public Boolean updatePhoneVerify(UserBindingPhoneUpdateRequest request) {
        //???????????????
        checkValidateCode(request.getPhone(), request.getCaptcha());

        //???????????????
        redisUtil.remove(getValidateCodeRedisKey(request.getPhone()));

        User user = getInfoException();

        if (!user.getPhone().equals(request.getPhone())) {
            throw new CrmebException("????????????????????????????????????");
        }

        return Boolean.TRUE;
    }

    /**
     * ???????????????
     */
    @Override
    public Boolean updatePhone(UserBindingPhoneUpdateRequest request) {
        //???????????????
        checkValidateCode(request.getPhone(), request.getCaptcha());

        //???????????????
        redisUtil.remove(getValidateCodeRedisKey(request.getPhone()));

        //??????????????????????????????????????????
        User user = getByPhone(request.getPhone());
        if (null != user) {
            throw new CrmebException("???????????????????????????");
        }

        //?????????????????????
        User bindUser = getInfoException();
        bindUser.setAccount(request.getPhone());
        bindUser.setPhone(request.getPhone());
        return updateById(bindUser);
    }

    /**
     * ????????????
     * @return UserCenterResponse
     */
    @Override
    public UserCenterResponse getUserCenter() {
        User currentUser = getInfo();
        if (ObjectUtil.isNull(currentUser)) {
            throw new CrmebException("????????????????????????????????????");
        }
        UserCenterResponse userCenterResponse = new UserCenterResponse();
        BeanUtils.copyProperties(currentUser, userCenterResponse);
        // ???????????????
        userCenterResponse.setCouponCount(storeCouponUserService.getUseCount(currentUser.getUid()));
        // ????????????
        userCenterResponse.setCollectCount(storeProductRelationService.getCollectCountByUid(currentUser.getUid()));

        // ??????????????????????????????
        Integer vipOpen = Integer.valueOf(systemConfigService.getValueByKey(SysConfigConstants.CONFIG_KEY_VIP_OPEN));
        if (vipOpen.equals(0)) {
            userCenterResponse.setVip(false);
        } else {// ??????
            userCenterResponse.setVip(userCenterResponse.getLevel() > 0);
            UserLevel userLevel = userLevelService.getUserLevelByUserId(currentUser.getUid());
            if (ObjectUtil.isNotNull(userLevel)) {
                SystemUserLevel systemUserLevel = systemUserLevelService.getByLevelId(userLevel.getLevelId());
                if (ObjectUtil.isNotNull(systemUserLevel)) {
                    userCenterResponse.setVipIcon(systemUserLevel.getIcon());
                    userCenterResponse.setVipName(systemUserLevel.getName());
                } else {
                    userCenterResponse.setVip(false);
                }
            } else {
                userCenterResponse.setVip(false);
            }
        }
        // ????????????
        String rechargeSwitch = systemConfigService.getValueByKey(SysConfigConstants.CONFIG_KEY_RECHARGE_SWITCH);
        if (StrUtil.isNotBlank(rechargeSwitch)) {
            userCenterResponse.setRechargeSwitch(Boolean.valueOf(rechargeSwitch));
        }

        // ?????????????????????????????????1.???????????????????????????2.????????????????????????????????????????????????
        String funcStatus = systemConfigService.getValueByKey(SysConfigConstants.CONFIG_KEY_BROKERAGE_FUNC_STATUS);
        if (funcStatus.equals("1")) {
            String brokerageStatus = systemConfigService.getValueByKey(SysConfigConstants.CONFIG_KEY_STORE_BROKERAGE_STATUS);
            if (brokerageStatus.equals("2")) {// ????????????
                userCenterResponse.setIsPromoter(true);
            }
        } else {
            userCenterResponse.setIsPromoter(false);
        }
        return userCenterResponse;
    }

    /**
     * ????????????id?????????????????? map??????
     *
     * @return HashMap<Integer, User>
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    @Override
    public HashMap<Integer, User> getMapListInUid(List<Integer> uidList) {
        List<User> userList = getListInUid(uidList);
        return getMapByList(userList);
    }

    /**
     * ????????????id?????????????????? map??????
     *
     * @return HashMap<Integer, User>
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    @Override
    public HashMap<Integer, User> getMapByList(List<User> list) {
        HashMap<Integer, User> map = new HashMap<>();
        if (null == list || list.size() < 1) {
            return map;
        }

        for (User user : list) {
            map.put(user.getUid(), user);
        }

        return map;
    }

    /**
     * ????????????????????????
     *
     * @param userId Integer ??????id
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    @Override
    public void repeatSignNum(Integer userId) {
        User user = new User();
        user.setUid(userId);
        user.setSignNum(0);
        updateById(user);
    }

    /**
     * ????????????
     *
     * @param id         String id
     * @param tagIdValue Integer ??????Id
     * @author Mr.Zhang
     * @since 2020-04-28
     */
    @Override
    public boolean tag(String id, String tagIdValue) {
        if (StrUtil.isBlank(id)) throw new CrmebException("????????????????????????");
        if (StrUtil.isBlank(tagIdValue)) throw new CrmebException("??????id????????????");

        //??????id??????
        List<Integer> idList = CrmebUtil.stringToArray(id);
        idList = idList.stream().distinct().collect(Collectors.toList());
        List<User> list = getListInUid(idList);
        if (CollUtil.isEmpty(list)) throw new CrmebException("????????????????????????");
        if (list.size() < 1) {
            throw new CrmebException("????????????????????????");
        }
        for (User user : list) {
            user.setTagId(tagIdValue);
        }
        return updateBatchById(list);
    }

    /**
     * ????????????id?????????????????????????????????
     *
     * @param userIdList List<Integer> ??????id??????
     * @return List<User>
     * @author Mr.Zhang
     * @since 2020-05-18
     */
    @Override
    public List<Integer> getSpreadPeopleIdList(List<Integer> userIdList) {
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.select(User::getUid); //????????????id
        lambdaQueryWrapper.in(User::getSpreadUid, userIdList); //xx???????????????
        List<User> list = userDao.selectList(lambdaQueryWrapper);

        if (null == list || list.size() < 1) {
            return new ArrayList<>();
        }
        return list.stream().map(User::getUid).distinct().collect(Collectors.toList());
    }

    /**
     * ????????????id?????????????????????????????????
     */
    @Override
    public List<UserSpreadPeopleItemResponse> getSpreadPeopleList(
            List<Integer> userIdList, String keywords, String sortKey, String isAsc, PageParamRequest pageParamRequest) {

        PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());

        Map<String, Object> map = new HashMap<>();
        map.put("userIdList", userIdList.stream().map(String::valueOf).distinct().collect(Collectors.joining(",")));
        if (StringUtils.isNotBlank(keywords)) {
            map.put("keywords", "%" + keywords + "%");
        }
        map.put("sortKey", "create_time");
        if (StringUtils.isNotBlank(sortKey)) {
            map.put("sortKey", sortKey);
        }
        map.put("sortValue", Constants.SORT_DESC);
        if (isAsc.toLowerCase().equals(Constants.SORT_ASC)) {
            map.put("sortValue", Constants.SORT_ASC);
        }

        return userDao.getSpreadPeopleList(map);
    }


    /**
     * ??????????????????token
     *
     * @author Mr.Zhang
     * @since 2020-04-29
     */
    @Override
    public String token(User user) throws Exception {
        TokenModel token = tokenManager.createToken(user.getAccount(), user.getUid().toString(), Constants.USER_TOKEN_REDIS_KEY_PREFIX);
        return token.getToken();
    }

    /**
     * ?????????????????????
     *
     * @author Mr.Zhang
     * @since 2020-04-29
     */
    private void checkValidateCode(String phone, String value) {
        Object validateCode = redisUtil.get(getValidateCodeRedisKey(phone));
        if (validateCode == null) {
            throw new CrmebException("??????????????????");
        }

        if (!validateCode.toString().equals(value)) {
            throw new CrmebException("???????????????");
        }
    }

    /**
     * ?????????????????????
     *
     * @param phone String ?????????
     * @return String
     * @author Mr.Zhang
     * @since 2020-04-29
     */
    @Override
    public String getValidateCodeRedisKey(String phone) {
        return SmsConstants.SMS_VALIDATE_PHONE + phone;
    }

    /**
     * ??????????????????????????????
     *
     * @author Mr.Zhang
     * @since 2020-04-29
     */
    public User getUserByAccount(String account) {
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getAccount, account);
        return userDao.selectOne(lambdaQueryWrapper);
    }

    /**
     * ?????????????????????
     *
     * @param phone     ?????????
     * @param spreadUid ???????????????
     * @return User
     */
    @Override
    public User registerPhone(String phone, Integer spreadUid) {
        User user = new User();
        user.setAccount(phone);
        user.setPwd(CommonUtil.createPwd(phone));
        user.setPhone(phone);
        user.setUserType(Constants.USER_LOGIN_TYPE_H5);
        user.setNickname(CommonUtil.createNickName(phone));
        user.setAvatar(systemConfigService.getValueByKey(Constants.USER_DEFAULT_AVATAR_CONFIG_KEY));
        Date nowDate = DateUtil.nowDateTime();
        user.setCreateTime(nowDate);
        user.setLastLoginTime(nowDate);

        // ?????????
        user.setSpreadUid(0);
        Boolean check = checkBingSpread(user, spreadUid, "new");
        if (check) {
            user.setSpreadUid(spreadUid);
            user.setSpreadTime(nowDate);
        }

        // ??????????????????????????????????????????
        List<StoreCouponUser> couponUserList = CollUtil.newArrayList();
        List<StoreCoupon> couponList = storeCouponService.findRegisterList();
        if (CollUtil.isNotEmpty(couponList)) {
            couponList.forEach(storeCoupon -> {
                //??????????????????????????????
                if (!storeCoupon.getIsFixedTime()) {
                    String endTime = DateUtil.addDay(DateUtil.nowDate(Constants.DATE_FORMAT), storeCoupon.getDay(), Constants.DATE_FORMAT);
                    storeCoupon.setUseEndTime(DateUtil.strToDate(endTime, Constants.DATE_FORMAT));
                    storeCoupon.setUseStartTime(DateUtil.nowDateTimeReturnDate(Constants.DATE_FORMAT));
                }

                StoreCouponUser storeCouponUser = new StoreCouponUser();
                storeCouponUser.setCouponId(storeCoupon.getId());
                storeCouponUser.setName(storeCoupon.getName());
                storeCouponUser.setMoney(storeCoupon.getMoney());
                storeCouponUser.setMinPrice(storeCoupon.getMinPrice());
                storeCouponUser.setStartTime(storeCoupon.getUseStartTime());
                storeCouponUser.setEndTime(storeCoupon.getUseEndTime());
                storeCouponUser.setUseType(storeCoupon.getUseType());
                storeCouponUser.setType(CouponConstants.STORE_COUPON_USER_TYPE_REGISTER);
                if (storeCoupon.getUseType() > 1) {
                    storeCouponUser.setPrimaryKey(storeCoupon.getPrimaryKey());
                }
                couponUserList.add(storeCouponUser);
            });
        }

        Boolean execute = transactionTemplate.execute(e -> {
            save(user);
            // ???????????????
            if (check) {
                updateSpreadCountByUid(spreadUid, "add");
            }
            // ?????????????????????
            if (CollUtil.isNotEmpty(couponUserList)) {
                couponUserList.forEach(couponUser -> couponUser.setUid(user.getUid()));
                storeCouponUserService.saveBatch(couponUserList);
                couponList.forEach(coupon -> storeCouponService.deduction(coupon.getId(), 1, coupon.getIsLimited()));
            }
            return Boolean.TRUE;
        });
        if (!execute) {
            throw new CrmebException("??????????????????!");
        }
        return user;
    }

    /**
     * ????????????????????????
     *
     * @param uid uid
     * @param type add or sub
     */
    public Boolean updateSpreadCountByUid(Integer uid, String type) {
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        if (type.equals("add")) {
            updateWrapper.setSql("spread_count = spread_count + 1");
        } else {
            updateWrapper.setSql("spread_count = spread_count - 1");
        }
        updateWrapper.eq("uid", uid);
        return update(updateWrapper);
    }

    /**
     * ??????/????????????
     *
     * @param uid            ??????id
     * @param price          ??????
     * @param brokeragePrice ????????????
     * @param type           ?????????add????????????sub?????????
     * @return Boolean
     */
    @Override
    public Boolean operationBrokerage(Integer uid, BigDecimal price, BigDecimal brokeragePrice, String type) {
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        if (type.equals("add")) {
            updateWrapper.setSql(StrUtil.format("brokerage_price = brokerage_price + {}", price));
        } else {
            updateWrapper.setSql(StrUtil.format("brokerage_price = brokerage_price - {}", price));
            updateWrapper.last(StrUtil.format(" and (brokerage_price - {} >= 0)", price));
        }
        updateWrapper.eq("uid", uid);
        updateWrapper.eq("brokerage_price", brokeragePrice);
        return update(updateWrapper);
    }

    /**
     * ??????/????????????
     *
     * @param uid      ??????id
     * @param price    ??????
     * @param nowMoney ????????????
     * @param type     ?????????add????????????sub?????????
     */
    @Override
    public Boolean operationNowMoney(Integer uid, BigDecimal price, BigDecimal nowMoney, String type) {
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        if (type.equals("add")) {
            updateWrapper.setSql(StrUtil.format("now_money = now_money + {}", price));
        } else {
            updateWrapper.setSql(StrUtil.format("now_money = now_money - {}", price));
            updateWrapper.last(StrUtil.format(" and (now_money - {} >= 0)", price));
        }
        updateWrapper.eq("uid", uid);
        updateWrapper.eq("now_money", nowMoney);
        return update(updateWrapper);
    }

    /**
     * ??????/????????????
     *
     * @param uid         ??????id
     * @param integral    ??????
     * @param nowIntegral ????????????
     * @param type        ?????????add????????????sub?????????
     * @return Boolean
     */
    @Override
    public Boolean operationIntegral(Integer uid, Integer integral, Integer nowIntegral, String type) {
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        if (type.equals("add")) {
            updateWrapper.setSql(StrUtil.format("integral = integral + {}", integral));
        } else {
            updateWrapper.setSql(StrUtil.format("integral = integral - {}", integral));
            updateWrapper.last(StrUtil.format(" and (integral - {} >= 0)", integral));
        }
        updateWrapper.eq("uid", uid);
        updateWrapper.eq("integral", nowIntegral);
        return update(updateWrapper);
    }

    /**
     * PC?????????????????????
     *
     * @param storeBrokerageStatus ???????????? 1-???????????????2-????????????
     * @param keywords             ????????????
     * @param dateLimit            ????????????
     * @param pageRequest          ????????????
     * @return PageInfo
     */
    @Override
    public PageInfo<User> getAdminSpreadPeopleList(String storeBrokerageStatus, String keywords, String dateLimit, PageParamRequest pageRequest) {
        Page<User> pageUser = PageHelper.startPage(pageRequest.getPage(), pageRequest.getLimit());
        LambdaQueryWrapper<User> lqw = new LambdaQueryWrapper<>();
        // id,??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        lqw.select(User::getUid, User::getNickname, User::getRealName, User::getPhone, User::getAvatar, User::getSpreadCount, User::getBrokeragePrice, User::getSpreadUid);
        if (storeBrokerageStatus.equals("1")) {
            lqw.eq(User::getIsPromoter, true);
        }
        lqw.apply("1 = 1");
        if (StrUtil.isNotBlank(keywords)) {
            lqw.and(i -> i.eq(User::getUid, keywords) //????????????
                    .or().like(User::getNickname, keywords) //??????
                    .or().like(User::getPhone, keywords)); //????????????
        }
        lqw.orderByDesc(User::getUid);
        List<User> userList = userDao.selectList(lqw);
        return CommonPage.copyPageInfo(pageUser, userList);
    }

    /**
     * ????????????????????????
     *
     * @param user      ????????????
     * @param spreadUid ?????????Uid
     * @param type      ????????????:new-????????????old????????????
     * @return Boolean
     * 1.??????????????????????????????
     * 2.??????????????????
     * 3.?????????????????????????????????
     * 4.??????????????????????????????????????????????????????spreadUid???????????????????????????
     * 5.?????????????????????????????????
     * *??????????????????????????????????????????????????????????????????A->B->A(???)
     */
    public Boolean checkBingSpread(User user, Integer spreadUid, String type) {
        if (spreadUid <= 0 || user.getSpreadUid() > 0) {
            return false;
        }
        if (ObjectUtil.isNotNull(user.getUid()) && user.getUid().equals(spreadUid)) {
            return false;
        }
        // ??????????????????????????????
        String isOpen = systemConfigService.getValueByKey(Constants.CONFIG_KEY_STORE_BROKERAGE_IS_OPEN);
        if (StrUtil.isBlank(isOpen) || isOpen.equals("0")) {
            return false;
        }
        if (type.equals("old")) {
            // ??????????????????????????????????????????????????????
            String bindType = systemConfigService.getValueByKey(Constants.CONFIG_KEY_DISTRIBUTION_TYPE);
            if (StrUtil.isBlank(bindType) || bindType.equals("1")) {
                return false;
            }
            if (user.getSpreadUid().equals(spreadUid)) {
                return false;
            }
        }
        // ??????????????????
        String model = systemConfigService.getValueByKey(Constants.CONFIG_KEY_STORE_BROKERAGE_MODEL);
        if (StrUtil.isBlank(model)) {
            return false;
        }
        // ???????????????
        User spreadUser = getById(spreadUid);
        if (ObjectUtil.isNull(spreadUser) || !spreadUser.getStatus()) {
            return false;
        }
        // ????????????????????????????????????
        if (model.equals("1") && !spreadUser.getIsPromoter()) {
            return false;
        }
        // ???????????????????????????????????????????????????
        if (ObjectUtil.isNotNull(user.getUid()) && spreadUser.getSpreadUid().equals(user.getUid())) {
            return false;
        }
        return true;
    }

    /**
     * ???????????????????????????spread_uid???????????????????????????
     *
     * @return List<User>
     */
    private List<User> getUserRelation(Integer userId) {
        List<User> userList = new ArrayList<>();
        User currUser = userDao.selectById(userId);
        if (currUser.getSpreadUid() > 0) {
            User spUser1 = userDao.selectById(currUser.getSpreadUid());
            if (null != spUser1) {
                userList.add(spUser1);
                if (spUser1.getSpreadUid() > 0) {
                    User spUser2 = userDao.selectById(spUser1.getSpreadUid());
                    if (null != spUser2) {
                        userList.add(spUser2);
                    }
                }
            }
        }
        return userList;
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param userId ??????id
     * @param type             0=???????????????1=???????????????2=???????????????3=??????????????????4=???????????????5=????????????
     * @param pageParamRequest ????????????
     * @return Object
     */
    @Override
    public Object getInfoByCondition(Integer userId, Integer type, PageParamRequest pageParamRequest) {
        switch (type) {
            case 0:
                return storeOrderService.findPaidListByUid(userId, pageParamRequest);
            case 1:
                AdminIntegralSearchRequest fmsq = new AdminIntegralSearchRequest();
                fmsq.setUid(userId);
                return userIntegralRecordService.findAdminList(fmsq, pageParamRequest);
            case 2:
                UserSign userSign = new UserSign();
                userSign.setUid(userId);
                return userSignService.getListByCondition(userSign, pageParamRequest);
            case 3:
                StoreCouponUserSearchRequest scur = new StoreCouponUserSearchRequest();
                scur.setUid(userId);
                return storeCouponUserService.findListByUid(userId, pageParamRequest);
            case 4:
                FundsMonitorSearchRequest fmsqq = new FundsMonitorSearchRequest();
                fmsqq.setUid(userId);
                fmsqq.setCategory(Constants.USER_BILL_CATEGORY_MONEY);
                return userBillService.getList(fmsqq, pageParamRequest);
            case 5:
                return getUserRelation(userId);
        }

        return new ArrayList<>();
    }

    /**
     * ????????????????????????
     *
     * @param userId Integer ??????id
     * @return Object
     */
    @Override
    public TopDetail getTopDetail(Integer userId) {
        TopDetail topDetail = new TopDetail();
        User currentUser = userDao.selectById(userId);
        topDetail.setUser(currentUser);
        topDetail.setBalance(currentUser.getNowMoney());
        topDetail.setIntegralCount(currentUser.getIntegral());
        topDetail.setMothConsumeCount(storeOrderService.getSumPayPriceByUidAndDate(userId, Constants.SEARCH_DATE_MONTH));
        topDetail.setAllConsumeCount(storeOrderService.getSumPayPriceByUid(userId));
        topDetail.setMothOrderCount(storeOrderService.getOrderCountByUidAndDate(userId, Constants.SEARCH_DATE_MONTH));
        topDetail.setAllOrderCount(storeOrderService.getOrderCountByUid(userId));
        return topDetail;
    }

    /**
     * ??????????????????????????????
     *
     * @param thirdUserRequest RegisterThirdUser ??????????????????????????????
     * @return User
     */
    @Override
    public User registerByThird(RegisterThirdUserRequest thirdUserRequest) {
        User user = new User();
        user.setAccount(DigestUtils.md5Hex(CrmebUtil.getUuid() + DateUtil.getNowTime()));
        user.setUserType(thirdUserRequest.getType());
        user.setNickname(thirdUserRequest.getNickName());
        String avatar = null;
        switch (thirdUserRequest.getType()) {
            case Constants.USER_LOGIN_TYPE_PUBLIC:
                avatar = thirdUserRequest.getHeadimgurl();
                break;
            case Constants.USER_LOGIN_TYPE_PROGRAM:
            case Constants.USER_LOGIN_TYPE_H5:
            case Constants.USER_LOGIN_TYPE_IOS_WX:
            case Constants.USER_LOGIN_TYPE_ANDROID_WX:
                avatar = thirdUserRequest.getAvatar();
                break;
        }
        user.setAvatar(avatar);
        user.setSpreadTime(DateUtil.nowDateTime());
        user.setSex(Integer.parseInt(thirdUserRequest.getSex()));
        user.setAddres(thirdUserRequest.getCountry() + "," + thirdUserRequest.getProvince() + "," + thirdUserRequest.getCity());
        return user;
    }

    /**
     * ??????????????????
     *
     * @param currentUserId ????????????id ????????????
     * @param spreadUserId  ?????????id
     * @return ??????????????????????????????
     */
    @Override
    public boolean spread(Integer currentUserId, Integer spreadUserId) {
        // ????????????????????????
        User currentUser = userDao.selectById(currentUserId);
        if (null == currentUser) throw new CrmebException("??????id:" + currentUserId + "?????????");
        User spreadUser = userDao.selectById(spreadUserId);
        if (null == spreadUser) throw new CrmebException("??????id:" + spreadUserId + "?????????");
        // ????????????????????????
        if (!spreadUser.getIsPromoter()) throw new CrmebException("??????id:" + spreadUserId + "?????????????????????");
        // ?????????????????????????????????
        LambdaQueryWrapper<User> lmq = new LambdaQueryWrapper<>();
        lmq.like(User::getPath, spreadUserId);
        lmq.eq(User::getUid, currentUserId);
        List<User> spreadUsers = userDao.selectList(lmq);
        if (spreadUsers.size() > 0) {
            throw new CrmebException("????????????????????????");
        }
        currentUser.setPath(currentUser.getPath() + spreadUser.getUid() + "/");
        currentUser.setSpreadUid(spreadUserId);
        currentUser.setSpreadTime(new Date());
        currentUser.setSpreadCount(currentUser.getSpreadCount() + 1);
        return userDao.updateById(currentUser) >= 0;
    }

    /**
     * ???????????????????????????????????????????????????????????????
     *
     * @param request ??????????????????
     * @return ??????????????????????????????
     */
    @Override
    public PageInfo<User> getUserListBySpreadLevel(RetailShopStairUserRequest request, PageParamRequest pageParamRequest) {
        if (request.getType().equals(1)) {// ???????????????
            return getFirstSpreadUserListPage(request, pageParamRequest);
        }
        if (request.getType().equals(2)) {// ???????????????
            return getSecondSpreadUserListPage(request, pageParamRequest);
        }
        return getAllSpreadUserListPage(request, pageParamRequest);
    }

    // ???????????????????????????
    private PageInfo<User> getFirstSpreadUserListPage(RetailShopStairUserRequest request, PageParamRequest pageParamRequest) {
        Page<User> userPage = PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(User::getUid, User::getAvatar, User::getNickname, User::getIsPromoter, User::getSpreadCount, User::getPayCount);
        queryWrapper.eq(User::getSpreadUid, request.getUid());
        if (StrUtil.isNotBlank(request.getNickName())) {
            queryWrapper.and(e -> e.like(User::getNickname, request.getNickName()).or().eq(User::getUid, request.getNickName())
                    .or().eq(User::getPhone, request.getNickName()));
        }
        List<User> userList = userDao.selectList(queryWrapper);
        return CommonPage.copyPageInfo(userPage, userList);
    }

    // ???????????????????????????
    private PageInfo<User> getSecondSpreadUserListPage(RetailShopStairUserRequest request, PageParamRequest pageParamRequest) {
        // ????????????????????????
        List<User> firstUserList = getSpreadListBySpreadIdAndType(request.getUid(), 1);
        if (CollUtil.isEmpty(firstUserList)) {
            return new PageInfo<>(CollUtil.newArrayList());
        }
        List<Integer> userIds = firstUserList.stream().map(User::getUid).distinct().collect(Collectors.toList());
        Page<User> userPage = PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(User::getUid, User::getAvatar, User::getNickname, User::getIsPromoter, User::getSpreadCount, User::getPayCount);
        queryWrapper.in(User::getSpreadUid, userIds);
        if (StrUtil.isNotBlank(request.getNickName())) {
            queryWrapper.and(e -> e.like(User::getNickname, request.getNickName()).or().eq(User::getUid, request.getNickName())
                    .or().eq(User::getPhone, request.getNickName()));
        }
        List<User> userList = userDao.selectList(queryWrapper);
        return CommonPage.copyPageInfo(userPage, userList);
    }

    // ???????????????????????????
    private PageInfo<User> getAllSpreadUserListPage(RetailShopStairUserRequest request, PageParamRequest pageParamRequest) {
        // ????????????????????????
        List<User> firstUserList = getSpreadListBySpreadIdAndType(request.getUid(), 0);
        if (CollUtil.isEmpty(firstUserList)) {
            return new PageInfo<>(CollUtil.newArrayList());
        }
        List<Integer> userIds = firstUserList.stream().map(User::getUid).distinct().collect(Collectors.toList());
        Page<User> userPage = PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(User::getUid, User::getAvatar, User::getNickname, User::getIsPromoter, User::getSpreadCount, User::getPayCount);
        queryWrapper.in(User::getUid, userIds);
        if (StrUtil.isNotBlank(request.getNickName())) {
            queryWrapper.and(e -> e.like(User::getNickname, request.getNickName()).or().eq(User::getUid, request.getNickName())
                    .or().eq(User::getPhone, request.getNickName()));
        }
        List<User> userList = userDao.selectList(queryWrapper);
        return CommonPage.copyPageInfo(userPage, userList);
    }

    /**
     * ???????????????????????????????????????????????????
     *
     * @param request ?????????????????????????????????
     * @return ??????????????????
     */
    @Override
    public PageInfo<SpreadOrderResponse> getOrderListBySpreadLevel(RetailShopStairUserRequest request, PageParamRequest pageParamRequest) {
        // ?????????????????????
        if (ObjectUtil.isNull(request.getType())) {
            request.setType(0);
        }
        List<User> userList = getSpreadListBySpreadIdAndType(request.getUid(), request.getType());
        if (CollUtil.isEmpty(userList)) {
            return new PageInfo<>();
        }

        List<Integer> userIds = userList.stream().map(User::getUid).distinct().collect(Collectors.toList());
        // ??????????????????????????????
        List<StoreOrder> orderList = storeOrderService.getOrderListStrByUids(userIds, request);
        if (CollUtil.isEmpty(orderList)) {
            return new PageInfo<>();
        }
        List<String> orderNoList = CollUtil.newArrayList();
        Map<String, StoreOrder> orderMap = CollUtil.newHashMap();
        orderList.forEach(e -> {
            orderNoList.add(e.getOrderId());
            orderMap.put(e.getOrderId(), e);
        });
        // ????????????????????????
        PageInfo<UserBrokerageRecord> recordPageInfo = userBrokerageRecordService.findListByLinkIdsAndLinkTypeAndUid(orderNoList, BrokerageRecordConstants.BROKERAGE_RECORD_LINK_TYPE_ORDER, request.getUid(), pageParamRequest);
        List<SpreadOrderResponse> responseList = recordPageInfo.getList().stream().map(e -> {
            SpreadOrderResponse response = new SpreadOrderResponse();
            StoreOrder storeOrder = orderMap.get(e.getLinkId());
            response.setId(storeOrder.getId());
            response.setOrderId(storeOrder.getOrderId());
            response.setRealName(storeOrder.getRealName());
            response.setUserPhone(storeOrder.getUserPhone());
            response.setPrice(e.getPrice());
            response.setUpdateTime(e.getUpdateTime());
            return response;
        }).collect(Collectors.toList());

        return CommonPage.copyPageInfo(recordPageInfo, responseList);
    }

    /**
     * ?????????????????????
     *
     * @param spreadUid ???Uid
     * @param type      ?????? 0 = ?????? 1=??????????????? 2=???????????????
     */
    private List<User> getSpreadListBySpreadIdAndType(Integer spreadUid, Integer type) {
        // ?????????????????????
        List<User> userList = getSpreadListBySpreadId(spreadUid);
        if (CollUtil.isEmpty(userList)) return userList;
        if (type.equals(1)) return userList;
        // ?????????????????????
        List<User> userSecondList = CollUtil.newArrayList();
        userList.forEach(user -> {
            List<User> childUserList = getSpreadListBySpreadId(user.getUid());
            if (CollUtil.isNotEmpty(childUserList)) {
                userSecondList.addAll(childUserList);
            }
        });
        if (type.equals(2)) {
            return userSecondList;
        }
        userList.addAll(userSecondList);
        return userList;
    }

    /**
     * ?????????????????????
     *
     * @param spreadUid ???Uid
     */
    private List<User> getSpreadListBySpreadId(Integer spreadUid) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getSpreadUid, spreadUid);
        return userDao.selectList(queryWrapper);
    }

    /**
     * ????????????id???????????????????????????
     *
     * @param userId ???????????????id
     * @return ??????????????????
     */
    @Override
    public boolean clearSpread(Integer userId) {
        User teamUser = getById(userId);
        User user = new User();
        user.setUid(userId);
        user.setPath("/0/");
        user.setSpreadUid(0);
        user.setSpreadTime(null);
        Boolean execute = transactionTemplate.execute(e -> {
            userDao.updateById(user);
            if (teamUser.getSpreadUid() > 0) {
                updateSpreadCountByUid(teamUser.getSpreadUid(), "sub");
            }
            return Boolean.TRUE;
        });
        return execute;
    }

    /**
     * ???????????????
     *
     * @param type             String ??????
     * @param pageParamRequest PageParamRequest ??????
     * @return List<User>
     */
    @Override
    public List<User> getTopSpreadPeopleListByDate(String type, PageParamRequest pageParamRequest) {
        PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("count(spread_count) as spread_count, spread_uid")
                .gt("spread_uid", 0)
                .eq("status", true);
        if (StrUtil.isNotBlank(type)) {
            dateLimitUtilVo dateLimit = DateUtil.getDateLimit(type);
            queryWrapper.between("create_time", dateLimit.getStartTime(), dateLimit.getEndTime());
        }
        queryWrapper.groupBy("spread_uid").orderByDesc("spread_count");
        List<User> spreadVoList = userDao.selectList(queryWrapper);
        if (spreadVoList.size() < 1) {
            return null;
        }

        List<Integer> spreadIdList = spreadVoList.stream().map(User::getSpreadUid).collect(Collectors.toList());
        if (spreadIdList.size() < 1) {
            return null;
        }

        ArrayList<User> userList = new ArrayList<>();
        //????????????
        HashMap<Integer, User> userVoList = getMapListInUid(spreadIdList);

        //??????????????????
        for (User spreadVo : spreadVoList) {
            User user = new User();
            User userVo = userVoList.get(spreadVo.getSpreadUid());
            user.setUid(spreadVo.getSpreadUid());
            user.setAvatar(userVo.getAvatar());
            user.setSpreadCount(spreadVo.getSpreadCount());
            if (StringUtils.isBlank(userVo.getNickname())) {
                user.setNickname(userVo.getPhone().substring(0, 2) + "****" + userVo.getPhone().substring(7));
            } else {
                user.setNickname(userVo.getNickname());
            }

            userList.add(user);
        }

        return userList;
    }

    /**
     * ???????????????
     *
     * @param minPayCount int ??????????????????
     * @param maxPayCount int ??????????????????
     * @return Integer
     */
    @Override
    public Integer getCountByPayCount(int minPayCount, int maxPayCount) {
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.between(User::getPayCount, minPayCount, maxPayCount);
        return userDao.selectCount(lambdaQueryWrapper);
    }

    /**
     * ????????????????????????????????????
     * @param spreadUid ?????????id
     */
    @Override
    public void bindSpread(Integer spreadUid) {
        //????????????????????????????????????????????????????????????????????????
        if (ObjectUtil.isNull(spreadUid) || spreadUid == 0) {
            return;
        }
        User user = getInfo();
        if (ObjectUtil.isNull(user)) {
            throw new CrmebException("?????????????????????,????????????");
        }

        loginService.bindSpread(user, spreadUid);
    }

    @Override
    public boolean updateBrokeragePrice(User user, BigDecimal newBrokeragePrice) {
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.set("brokerage_price", newBrokeragePrice)
                .eq("uid", user.getUid()).eq("brokerage_price", user.getBrokeragePrice());
        return userDao.update(user, updateWrapper) > 0;
    }

    /**
     * ???????????????
     *
     * @param request ????????????
     * @return Boolean
     */
    @Override
    public Boolean editSpread(UserUpdateSpreadRequest request) {
        Integer userId = request.getUserId();
        Integer spreadUid = request.getSpreadUid();
        if (userId.equals(spreadUid)) {
            throw new CrmebException("??????????????????????????????");
        }
        User user = getById(userId);
        if (ObjectUtil.isNull(user)) {
            throw new CrmebException("???????????????");
        }
        if (user.getSpreadUid().equals(spreadUid)) {
            throw new CrmebException("?????????????????????????????????");
        }
        Integer oldSprUid = user.getSpreadUid();

        User spreadUser = getById(spreadUid);
        if (ObjectUtil.isNull(spreadUser)) {
            throw new CrmebException("?????????????????????");
        }
        if (spreadUser.getSpreadUid().equals(userId)) {
            throw new CrmebException("????????????????????????????????????");
        }

        User tempUser = new User();
        tempUser.setUid(userId);
        tempUser.setSpreadUid(spreadUid);
        tempUser.setSpreadTime(DateUtil.nowDateTime());
        Boolean execute = transactionTemplate.execute(e -> {
            updateById(tempUser);
            updateSpreadCountByUid(spreadUid, "add");
            if (oldSprUid > 0) {
                updateSpreadCountByUid(oldSprUid, "sub");
            }
            return Boolean.TRUE;
        });
        return execute;
    }

    /**
     * ??????????????????
     *
     * @param user     ??????
     * @param integral ??????
     * @param type     ??????add?????????sub
     * @return ????????????????????????
     */
    @Override
    public Boolean updateIntegral(User user, Integer integral, String type) {
        LambdaUpdateWrapper<User> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
        if (type.equals("add")) {
            lambdaUpdateWrapper.set(User::getIntegral, user.getIntegral() + integral);
        } else {
            lambdaUpdateWrapper.set(User::getIntegral, user.getIntegral() - integral);
        }
        lambdaUpdateWrapper.eq(User::getUid, user.getUid());
        if (type.equals("sub")) {
            lambdaUpdateWrapper.apply(StrUtil.format(" integral - {} >= 0", integral));
        }
        return update(lambdaUpdateWrapper);
    }

    /**
     * ????????????????????????
     *
     * @param keywords             ????????????
     * @param dateLimit            ????????????
     * @param storeBrokerageStatus ???????????????1-???????????????2-????????????
     * @return List<User>
     */
    @Override
    public List<User> findDistributionList(String keywords, String dateLimit, String storeBrokerageStatus) {
        LambdaQueryWrapper<User> lqw = new LambdaQueryWrapper<>();
        if (storeBrokerageStatus.equals("1")) {
            lqw.eq(User::getIsPromoter, true);
        }
        if (StrUtil.isNotBlank(dateLimit)) {
            dateLimitUtilVo dateLimitVo = DateUtil.getDateLimit(dateLimit);
            lqw.between(User::getCreateTime, dateLimitVo.getStartTime(), dateLimitVo.getEndTime());
        }
        if (StrUtil.isNotBlank(keywords)) {
            lqw.and(i -> i.like(User::getRealName, keywords) //????????????
                    .or().like(User::getPhone, keywords) //????????????
                    .or().like(User::getNickname, keywords) //????????????
                    .or().like(User::getUid, keywords)); //uid
        }
        return userDao.selectList(lqw);
    }

    /**
     * ????????????????????????
     *
     * @param ids       ?????????id??????
     * @param dateLimit ????????????
     * @return Integer
     */
    @Override
    public Integer getDevelopDistributionPeopleNum(List<Integer> ids, String dateLimit) {
        LambdaQueryWrapper<User> lqw = Wrappers.lambdaQuery();
        lqw.in(User::getSpreadUid, ids);
        if (StrUtil.isNotBlank(dateLimit)) {
            dateLimitUtilVo dateLimitVo = DateUtil.getDateLimit(dateLimit);
            lqw.between(User::getCreateTime, dateLimitVo.getStartTime(), dateLimitVo.getEndTime());
        }
        return userDao.selectCount(lqw);
    }

    /**
     * ??????User Group id
     *
     * @param groupId ????????????GroupId
     */
    @Override
    public void clearGroupByGroupId(String groupId) {
        LambdaUpdateWrapper<User> upw = Wrappers.lambdaUpdate();
        upw.set(User::getGroupId, "").eq(User::getGroupId, groupId);
        update(upw);
    }

    /**
     * ????????????
     *
     * @param userRequest ????????????
     * @return Boolean
     */
    @Override
    public Boolean updateUser(UserUpdateRequest userRequest) {
        User tempUser = getById(userRequest.getUid());
        User user = new User();
        BeanUtils.copyProperties(userRequest, user);
        if (ObjectUtil.isNull(userRequest.getLevel())) {
            user.setLevel(0);
        }
        Boolean execute = transactionTemplate.execute(e -> {
            updateById(user);
            if (ObjectUtil.isNotNull(userRequest.getLevel()) && !tempUser.getLevel().equals(userRequest.getLevel())) {
                // ?????????????????????
                UserLevel userLevel = userLevelService.getUserLevelByUserId(tempUser.getUid());
                if (ObjectUtil.isNotNull(userLevel)) {
                    userLevel.setIsDel(true);
                    userLevelService.updateById(userLevel);
                }

                SystemUserLevel systemUserLevel = systemUserLevelService.getByLevelId(userRequest.getLevel());
                UserLevel newLevel = new UserLevel();
                newLevel.setUid(tempUser.getUid());
                newLevel.setLevelId(systemUserLevel.getId());
                newLevel.setGrade(systemUserLevel.getGrade());
                newLevel.setStatus(true);
                newLevel.setMark(StrUtil.format("??????????????? {},???{}?????????????????????????????????{}", tempUser.getNickname(), DateUtil.nowDateTimeStr(), systemUserLevel.getName()));
                newLevel.setDiscount(systemUserLevel.getDiscount());
                newLevel.setCreateTime(DateUtil.nowDateTime());
                userLevelService.save(newLevel);
            }
            if (ObjectUtil.isNull(userRequest.getLevel()) && tempUser.getLevel() > 0) {
                UserLevel userLevel = userLevelService.getUserLevelByUserId(tempUser.getUid());
                userLevel.setIsDel(true);
                userLevelService.updateById(userLevel);
            }
            return Boolean.TRUE;
        });
        return execute;
    }

    /**
     * ???????????????????????????
     * @param phone ???????????????
     * @return ????????????
     */
    @Override
    public User getByPhone(String phone) {
        LambdaQueryWrapper<User> lqw = new LambdaQueryWrapper<>();
        lqw.eq(User::getPhone, phone);
        return userDao.selectOne(lqw);
    }

    /**
     * ???????????????????????????
     * @param id ??????uid
     * @param phone ?????????
     * @return Boolean
     */
    @Override
    public Boolean updateUserPhone(Integer id, String phone) {
        boolean matchPhone = ReUtil.isMatch(RegularConstants.PHONE, phone);
        if (!matchPhone) {
            throw new CrmebException("???????????????????????????????????????????????????");
        }
        User user = getById(id);
        if (ObjectUtil.isNull(user)) {
            throw new CrmebException("?????????????????????");
        }
        if (phone.equals(user.getPhone())) {
            throw new CrmebException("????????????????????????");
        }

        //??????????????????????????????????????????
        User tempUser = getByPhone(phone);
        if (ObjectUtil.isNotNull(tempUser)) {
            throw new CrmebException("???????????????????????????");
        }

        User newUser = new User();
        newUser.setUid(id);
        newUser.setPhone(phone);
        newUser.setAccount(phone);
        return userDao.updateById(newUser) > 0;
    }

    /**
     * ?????????????????????????????????id??????
     * @param nikeName ?????????????????????
     * @return List
     */
    @Override
    public List<Integer> findIdListLikeName(String nikeName) {
        LambdaQueryWrapper<User> lqw = Wrappers.lambdaQuery();
        lqw.select(User::getUid);
        lqw.like(User::getNickname, nikeName);
        List<User> userList = userDao.selectList(lqw);
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(User::getUid).collect(Collectors.toList());
    }
}
