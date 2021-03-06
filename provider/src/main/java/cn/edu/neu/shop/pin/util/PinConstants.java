package cn.edu.neu.shop.pin.util;

public class PinConstants {

    public static final String WECHAT_APP_ID = "wx2c6c6a9247cd39b0";
    public static final String WECHAT_SECRET_KEY = "94b2114db2aa49376df54fc6e0969962";

    // 锁的默认键值
    public static final String LOCK_KEY_ORDER_INDIVIDUAL = "or-ind";
    public static final String LOCK_KEY_ORDER_GROUP = "or-grp";

    // 状态码
    public class StatusCode {
        public static final int SUCCESS = 200;
        public static final int INVALID_CREDENTIAL = 401;
        public static final int INVALID_DATA = 402;
        public static final int PERMISSION_DENIED = 403;

        public static final int INTERNAL_ERROR = 500;
        public static final int PRODUCT_SOLD_OUT = 800;

        public static final int PAY_REPEAT_PAYMENT = 900; // 重复支付已经支付的订单
        public static final int PAY_INSUFFICIENT_BALANCE = 901; // 余额不足
    }

    public class ResponseMessage {
        public static final String SUCCESS = "请求成功";
        public static final String INVALID_CREDENTIAL = "错误的登录凭据";
        public static final String INVALID_DATA = "数据有误";
        public static final String PERMISSION_DENIED = "权限不足";
        public static final String INTERNAL_ERROR = "服务器错误";
        public static final String PRODUCT_SOLD_OUT = "存在商品库存不足";
    }

    public class PayType {
        public static final String WECHAT = "WECHAT";
        public static final String BALANCE = "BALANCE";
        public static final String BOTH = "WECHAT BALANCE";
    }

    public class ProductStatus {
        public static final int ALARM = 10;
    }

    public class MessageQueueKey {
        public static final String GROUP = "GROUP";
        public static final String USER = "USER";
    }

}
