package cn.edu.neu.shop.pin.service;

import cn.edu.neu.shop.pin.exception.OrderItemsAreNotInTheSameStoreException;
import cn.edu.neu.shop.pin.exception.ProductSoldOutException;
import cn.edu.neu.shop.pin.exception.RecordNotFoundException;
import cn.edu.neu.shop.pin.mapper.PinOrderIndividualMapper;
import cn.edu.neu.shop.pin.model.PinOrderIndividual;
import cn.edu.neu.shop.pin.model.PinOrderItem;
import cn.edu.neu.shop.pin.model.PinUser;
import cn.edu.neu.shop.pin.model.PinUserAddress;
import cn.edu.neu.shop.pin.service.finance.UserBalanceService;
import cn.edu.neu.shop.pin.service.message.TemplateMessageService;
import cn.edu.neu.shop.pin.util.base.AbstractService;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class OrderIndividualService extends AbstractService<PinOrderIndividual> {

    public static final int STATUS_CONFIRM_SUCCESS = 0;
    public static final int STATUS_CONFIRM_PERMISSION_DENIED = -1;
    public static final int STATUS_CONFIRM_FAILED = -2;

    public static final int STATUS_ORDER_SUCCESS = 0;

    public static final int STATUS_ORDER_FAILURE = -1;


    private final UserRoleListTransferService userRoleListTransferService;

    private final ProductService productService;

    private final StoreService storeService;

    private final OrderItemService orderItemService;

    private final AddressService addressService;

    private final PinOrderIndividualMapper pinOrderIndividualMapper;

    private final UserBalanceService userBalanceService;

    @Autowired
    private TemplateMessageService templateMessageService;

    @Autowired
    private OrderIndividualService orderIndividualService;

    public OrderIndividualService(UserRoleListTransferService userRoleListTransferService, ProductService productService, StoreService storeService, OrderItemService orderItemService, AddressService addressService, PinOrderIndividualMapper pinOrderIndividualMapper, UserBalanceService userBalanceService) {
        this.userRoleListTransferService = userRoleListTransferService;
        this.productService = productService;
        this.storeService = storeService;
        this.orderItemService = orderItemService;
        this.addressService = addressService;
        this.pinOrderIndividualMapper = pinOrderIndividualMapper;
        this.userBalanceService = userBalanceService;
    }

    /**
     * 获取最近三个月的OrderIndividual信息
     *
     * @param userId 用户ID
     * @return OrderIndividual的List
     */
    public List<PinOrderIndividual> getRecentThreeMonthsOrderIndividuals(Integer userId) {
        List<PinOrderIndividual> orderIndividuals =
                pinOrderIndividualMapper.getRecentThreeMonthsOrderIndividuals(userId);
        for (PinOrderIndividual o : orderIndividuals) {
            o.setOrderItems(orderItemService.getOrderItemsByOrderIndividualId(o.getId()));
            o.setStore(storeService.getStoreById(o.getStoreId()));
        }
        return orderIndividuals;
    }

    /**
     * 传入一串PinOrderIndividual，返回它们对应的用户list
     *
     * @param list 一串PinOrderIndividual
     * @return 返回它们对应的用户list
     */
    List<PinUser> getUsers(List<PinOrderIndividual> list) {
        List<PinUser> users = new ArrayList<>();
        for (PinOrderIndividual item : list) {
            users.add(userRoleListTransferService.findById(item.getUserId()));
        }
        return users;
    }

    /**
     * 提交订单，即把一条OrderItem记录变为Submitted
     *
     * @param user      用户
     * @param list      购物车列表
     * @param addressId 地址ID
     * @return 订单信息
     * @throws OrderItemsAreNotInTheSameStoreException 结算时商品不属于同一家店铺
     * @throws ProductSoldOutException                 商品已售空
     */
    public PinOrderIndividual addOrderIndividual(PinUser user, List<PinOrderItem> list, Integer addressId, String userRemark) throws OrderItemsAreNotInTheSameStoreException, ProductSoldOutException {
        PinUserAddress address = addressService.findById(addressId);
        if (address == null) {
            throw new RecordNotFoundException("地址ID不正确");
        }
        boolean isSameStore = productService.isBelongSameStore(list);
        //如果属于一家店铺
        if (isSameStore) {
            Integer amount = orderItemService.getProductAmount(list);
            if (amount == -1) {
                //库存不够，只能终止这次创建orderIndividual
                throw new ProductSoldOutException("库存不足");
            }
            int storeId = productService.getProductById(list.get(0).getProductId()).getStoreId();    // 店铺id
            BigDecimal originallyPrice = orderItemService.getProductTotalPrice(list);   // 计算本来的价格
            BigDecimal shippingFee = orderItemService.getAllShippingFee(list);  // 邮费
            BigDecimal totalPrice = originallyPrice.add(shippingFee);   //总费用
            // OrderItemService.PayDetail payDetail = orderItemService.new PayDetail(user.getId(), totalPrice);    //支付详情
            BigDecimal totalCost = orderItemService.getTotalCost(list);
            String addressString = address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail();
            PinOrderIndividual orderIndividual = new PinOrderIndividual(null, storeId, user.getId(),
                    address.getRealName(), address.getPhone(), addressString,
                    orderItemService.getProductAmount(list), totalPrice/*总价格 邮费加本来的费用*/,
                    shippingFee, null, /*卖家可以改动实际支付的邮费，修改的时候总价格也要修改，余额支付，实际支付也要改*/
                    null, null, false, null,
                    new Date(), 0, 0, null, null, null,
                    null, null, null, null, null, null, null, userRemark, null, totalCost);
            this.save(orderIndividual);
            //将list中的PinOrderItem挂载到PinOrderIndividual上
            orderItemService.mountOrderItems(list, orderIndividual.getId());
            return orderIndividual;
        } else {
            //如果不属于一家店铺
            throw new OrderItemsAreNotInTheSameStoreException("不属于一家店铺");
        }
    }

    /**
     * @param orderGroupId 团单ID
     * @return 团单list
     * @author flyhero
     * 根据OrderGroupId获取在此团单内的OrderIndividual的List
     */
    public List<PinOrderIndividual> getOrderIndividualsByOrderGroupId(Integer orderGroupId) {
        PinOrderIndividual orderIndividual = new PinOrderIndividual();
        orderIndividual.setOrderGroupId(orderGroupId);
        return pinOrderIndividualMapper.select(orderIndividual);
    }

    /**
     * 获取订单数
     *
     * @param storeId 商店ID
     * @return 七天内订单树木的数组
     */
    public Integer[] getOrders(Integer storeId) {
        Integer[] order = new Integer[7];
        java.util.Date date = new java.util.Date();
        date = getDateByOffset(date, 1); // tomorrow
        for (int i = 0; i < 7; i++) {
            java.util.Date toDate = date;
            date = getDateByOffset(date, -1); // yesterday
            java.util.Date fromDate = date;
            order[i] = pinOrderIndividualMapper.getNumberOfOrder(fromDate, toDate, storeId);
        }
        return order;
    }

    private java.util.Date getDateByOffset(java.util.Date today, Integer delta) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) + delta);
        return calendar.getTime();
    }

    /**
     * @param keyWord 关键词
     * @return 返回符合关键词的 PinOrderIndividual 数组，
     * 每个 PinOrderIndividual 里面有多个 PinOrderItem
     * 每个 PinOrderItem 里面有一个 PinProduct
     */
    public List<PinOrderIndividual> getAllWithProductsByKeyWord(String keyWord, Integer storeId) {
        return pinOrderIndividualMapper.getAllWithProductsByKeyWord(keyWord == null ? "" : keyWord, storeId);
    }

    /**
     * TODO：待测试
     *
     * @param list      待过滤的PinOrderIndividual的数组
     * @param orderType 传过来的orderType
     * @return 返回过滤过后的list
     */
    public List<PinOrderIndividual> getOrdersByOrderType(List<PinOrderIndividual> list, Integer orderType) {
        List<PinOrderIndividual> returnList = new ArrayList<>();
        switch (orderType) {
            case 0://全部
                return list;
            case 1://未付款
                for (PinOrderIndividual item : list) {
                    if (!item.getPaid())
                        returnList.add(item);
                }
                break;
            case 2://待发货
                for (PinOrderIndividual item : list) {
                    if (item.getStatus() == PinOrderIndividual.STATUS_DEPENDING_TO_SHIP)
                        returnList.add(item);
                }
                break;
            case 3://待收货
                for (PinOrderIndividual item : list) {
                    if (item.getStatus() == PinOrderIndividual.STATUS_SHIPPED)
                        returnList.add(item);
                }
                break;
            case 4://待评价
                for (PinOrderIndividual item : list) {
                    if (item.getStatus() == PinOrderIndividual.STATUS_PENDING_COMMENT)
                        returnList.add(item);
                }
                break;
            case 5://交易完成
                for (PinOrderIndividual item : list) {
                    if (item.getStatus() == PinOrderIndividual.STATUS_COMMENTED)
                        returnList.add(item);
                }
                break;
            case 6://退款中
                for (PinOrderIndividual item : list) {
                    if (item.getStatus() == PinOrderIndividual.REFUND_STATUS_APPLYING)
                        returnList.add(item);
                }
                break;
            case 7://已退款
                for (PinOrderIndividual item : list) {
                    if (item.getStatus() == PinOrderIndividual.REFUND_STATUS_FINISHED)
                        returnList.add(item);
                }
                break;
        }
        return returnList;
    }

    /**
     * TODO: 待测试
     *
     * @param list      待过滤的PinOrderIndividual的数组
     * @param orderDate 时间条件对应的码
     * @param queryType 前端传入的json
     * @return 返回符合条件的数组
     */
    public List<PinOrderIndividual> getOrdersByOrderDate(List<PinOrderIndividual> list, Integer orderDate, JSONObject queryType) {
        List<PinOrderIndividual> returnList = new ArrayList<>();
        java.util.Date createTime;
        java.util.Date now = new java.util.Date();
        Calendar caNow = Calendar.getInstance();
        caNow.setTime(now);
        Calendar caCreate = Calendar.getInstance();
        for (PinOrderIndividual item : list) {
            createTime = item.getCreateTime();
            caCreate.setTime(createTime);

            switch (orderDate) {
                case 0://全部
                    returnList.add(item);
                case 1://昨天
                    caCreate.add(Calendar.DAY_OF_YEAR, 1);
                    if ((caCreate.get(Calendar.DAY_OF_YEAR) == caNow.get(Calendar.DAY_OF_YEAR)) &&
                            (caCreate.get(Calendar.YEAR) == caNow.get(Calendar.YEAR)))
                        returnList.add(item);
                    break;
                case 2://今天
                    if (caCreate.get(Calendar.DAY_OF_YEAR) == caNow.get(Calendar.DAY_OF_YEAR))
                        returnList.add(item);
                    break;
                case 3://本周
                    if ((caCreate.get(Calendar.YEAR) == caNow.get(Calendar.YEAR)) &&
                            (caCreate.get(Calendar.WEEK_OF_YEAR) == caNow.get(Calendar.WEEK_OF_YEAR)))
                        returnList.add(item);
                    break;
                case 4://本月
                    if ((caCreate.get(Calendar.YEAR) == caNow.get(Calendar.YEAR)) &&
                            (caCreate.get(Calendar.MONTH) == caNow.get(Calendar.MONTH)))
                        returnList.add(item);
                    break;
                case 5://本季度
                    int createMonth = caCreate.get(Calendar.MONTH) + 1; //创建时的月份
                    int rightMonth = caNow.get(Calendar.MONTH) + 1; //现在的月份
                    if (caCreate.get(Calendar.YEAR) == caNow.get(Calendar.YEAR)) {
                        if (createMonth >= 1 && rightMonth >= 1 && createMonth <= 3 && rightMonth <= 3)
                            returnList.add(item);
                        else if (createMonth >= 4 && rightMonth >= 4 && createMonth <= 6 && rightMonth <= 6)
                            returnList.add(item);
                        else if (createMonth >= 7 && rightMonth >= 7 && createMonth <= 9 && rightMonth <= 9)
                            returnList.add(item);
                        else if (createMonth >= 10 && rightMonth >= 10 && createMonth <= 12 && rightMonth <= 12)
                            returnList.add(item);
                    }
                    break;
                case 6://本年
                    if (caCreate.get(Calendar.YEAR) == caNow.get(Calendar.YEAR))
                        returnList.add(item);
                    break;
                case 7://自定义
                    java.util.Date begin = queryType.getDate("begin");
                    java.util.Date end = queryType.getDate("end");
                    if (begin != null && end != null) {
                        if (caCreate.getTime().getTime() >= begin.getTime() && caCreate.getTime().getTime() <= end.getTime())
                            returnList.add(item);
                    } else if (begin == null && end != null) {
                        if (caCreate.getTime().getTime() <= end.getTime())
                            returnList.add(item);
                    } else if (begin != null) {
                        if (caCreate.getTime().getTime() >= begin.getTime())
                            returnList.add(item);
                    } else {
                        returnList.add(item);
                    }
                    break;
            }
        }
        return returnList;
    }

    /**
     * 未发货商品数量
     *
     * @param storeId 店铺ID
     * @return 未发货商品数量
     */
    public Integer getProductNotShip(Integer storeId) {
        return pinOrderIndividualMapper.getNumberOfOrderNotShip(storeId);
    }

    public Integer getOrderRefund(Integer storeId) {
        return pinOrderIndividualMapper.getNumberOfOrderRefund(storeId);
    }

    /**
     * @param list       传入 一个对象 的list
     * @param pageNumber 传入的页码数
     * @param pageSize   传入一页的size
     * @return 返回要查找的那页
     */
    public List getOrdersByPageNumAndSize(List list, Integer pageNumber, Integer pageSize) {
        if (pageNumber * pageSize < list.size()) {
            return list.subList((pageNumber - 1) * pageSize, pageNumber * pageSize);
        } else {
            return list.subList((pageNumber - 1) * pageSize, list.size());
        }
    }

    /**
     * @author flyhero
     * 确认收货
     */
    public Integer confirmReceipt(Integer userId, Integer orderIndividualId) {
        PinOrderIndividual orderIndividual = this.findById(orderIndividualId);
        if (!Objects.equals(userId, orderIndividual.getUserId())) {
            // 用户ID不符
            return STATUS_CONFIRM_PERMISSION_DENIED;
        }
        if (orderIndividual.getStatus() != PinOrderIndividual.STATUS_SHIPPED) {
            // 订单状态不符合确认收货条件
            return STATUS_CONFIRM_FAILED;
        }
        // 执行确认收货操作
        orderIndividual.setConfirmReceiptTime(new Date());
        orderIndividual.setStatus(PinOrderIndividual.STATUS_PENDING_COMMENT);
        this.update(orderIndividual);
        // TODO: 给商家推送确认收货
        try {
            templateMessageService.sendConfirmReceiptMessageToIndividualOrderOwner(orderIndividual);
        }catch (Exception ex) {
            ex.printStackTrace();
        }
        return STATUS_CONFIRM_SUCCESS;
    }

    public void updateOrderStatusNotExpress(Integer orderIndividualId, String deliveryType, Date deliveryTime) {
        pinOrderIndividualMapper.updateOrderDeliveryTypeNotExpress(orderIndividualId, deliveryType, deliveryTime);
    }

    public void updateOrderStatusIsExpress(Integer orderIndividualId, String deliveryType, String deliveryName, Integer deliveryId, Date deliveryTime) {
        pinOrderIndividualMapper.updateOrderDeliveryTypeIsExpress(orderIndividualId, deliveryType, deliveryName, deliveryId, deliveryTime);
        try {
            // 发送模板消息
            templateMessageService.sendOrderShippedMessageToIndividualOrderOwner(orderIndividualService.findById(orderIndividualId));
        }catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void updateMerchantRemark(Integer orderIndividualId, String merchantRemark) {
        pinOrderIndividualMapper.updateMerchantRemark(orderIndividualId, merchantRemark);
    }

    public Integer updateRefundOrder(Integer orderIndividualId, String refundReasonImage, String refundReasonExplain, Date date, BigDecimal refundPrice){
        PinOrderIndividual orderIndividual = findById(orderIndividualId);
        BigDecimal totalPrice = orderIndividual.getRefundPrice();
        if(totalPrice.compareTo(refundPrice) <= 0){
            pinOrderIndividualMapper.updateRefundOrder(orderIndividualId, refundReasonImage, refundReasonExplain, date, refundPrice);
            return STATUS_ORDER_SUCCESS;
        } else {
            return STATUS_ORDER_FAILURE;
        }

    }

    public void updateRefundSuccess(Integer userId, Integer orderIndividualId) {
        PinOrderIndividual orderIndividual = findById(orderIndividualId);
        pinOrderIndividualMapper.updateRefundSuccess(orderIndividualId);
        userBalanceService.returnRefundBalanceFromIndividualOrder(userId, orderIndividualId, orderIndividual.getRefundPrice());
    }

    public void updateRefundFailure(Integer orderIndividualId, String refundRefuseReason) {
        pinOrderIndividualMapper.updateRefundFailure(orderIndividualId, refundRefuseReason);
    }
}
