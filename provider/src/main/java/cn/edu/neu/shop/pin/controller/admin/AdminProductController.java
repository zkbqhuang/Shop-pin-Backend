package cn.edu.neu.shop.pin.controller.admin;

import cn.edu.neu.shop.pin.model.PinProduct;
import cn.edu.neu.shop.pin.model.PinProductAttributeDefinition;
import cn.edu.neu.shop.pin.model.PinProductAttributeValue;
import cn.edu.neu.shop.pin.service.*;
import cn.edu.neu.shop.pin.util.PinConstants;
import cn.edu.neu.shop.pin.util.ResponseWrapper;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping(value = "/goods")
public class AdminProductController {

    private final ProductService productService;

    private final ProductCategoryService productCategoryService;

    private final ProductAttributeDefinitionService definitionService;

    private final PinProductAttributeValueService valueService;

    @Autowired
    public AdminProductController(ProductService productService, ProductCategoryService productCategoryService, ProductAttributeDefinitionService definitionService, PinProductAttributeValueService valueService) {
        this.productService = productService;
        this.productCategoryService = productCategoryService;
        this.definitionService = definitionService;
        this.valueService = valueService;
    }

    /**
     * 返回不同类型商品列表
     *
     * @param httpServletRequest http请求体
     * @param requestJSON        请求体JSON
     * @return 响应JSON
     */
    @PostMapping("/goods-list")
    public JSONObject getProducts(HttpServletRequest httpServletRequest, @RequestBody JSONObject requestJSON) {
        try {
            Integer storeId = Integer.valueOf(httpServletRequest.getHeader("Current-Store"));
            String queryType = requestJSON.getString("queryType");
            JSONObject data = new JSONObject();
            switch (queryType) {
                case "SALING":
                    List<PinProduct> saling = productService.getIsShownProductInfo(storeId);
                    data.put("goodsList", saling);
                    return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, PinConstants.ResponseMessage.SUCCESS, data);
                case "READY":
                    List<PinProduct> ready = productService.getIsReadyProductInfo(storeId);
                    data.put("goodsList", ready);
                    return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, PinConstants.ResponseMessage.SUCCESS, data);
                case "OUT":
                    List<PinProduct> out = productService.getIsOutProductInfo(storeId);
                    int flag;
                    //删除掉 每一个库存都有的product
                    for (PinProduct item : out) {
                        flag = 0;
                        for (PinProductAttributeValue inner : item.getProductAttributeValues()) {
                            if (inner.getStock() == 0)
                                flag = 1;
                        }
                        if (flag == 0)
                            out.remove(item);
                    }
                    data.put("goodsList", out);
                    return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, PinConstants.ResponseMessage.SUCCESS, data);
                case "ALARM":
                    List<PinProduct> alarm = productService.getIsAlarmProductInfo(storeId);
                    //删除掉 每一个库存都有的product
                    for (PinProduct item : alarm) {
                        flag = 0;
                        for (PinProductAttributeValue inner : item.getProductAttributeValues()) {
                            if (inner.getStock() <= 10)
                                flag = 1;
                        }
                        if (flag == 0)
                            alarm.remove(item);
                    }
                    data.put("goodsList", alarm);
                    return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, PinConstants.ResponseMessage.SUCCESS, data);
                default:
                    return ResponseWrapper.wrap(PinConstants.StatusCode.INTERNAL_ERROR, "获取失败", null);
            }
        } catch (Exception e) {
            return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, e.getMessage(), null);
        }
    }

    /**
     * 商铺所有者管理本店铺的商品
     * 商品分类部分 获取父级、子级分类名及一些商品信息
     *
     * @param httpServletRequest 请求体JSON
     * @return 响应JSON
     */
    @GetMapping("/goods-category")
    public JSONObject getProductFromSameStore(HttpServletRequest httpServletRequest) {
        try {
            String currentStoreId = httpServletRequest.getHeader("Current-Store");
            List<JSONObject> list = productService.getProductInfoFromSameStore(Integer.parseInt(currentStoreId));
            JSONObject data = new JSONObject();
            data.put("list", list);
            return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, PinConstants.ResponseMessage.SUCCESS, data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseWrapper.wrap(PinConstants.StatusCode.INTERNAL_ERROR, e.getMessage(), null);
        }
    }

    @GetMapping("/category-list")
    public JSONObject getProductCatrgory() {
        try {
            JSONArray array = productCategoryService.getProductCategory();
            JSONObject categoryList = new JSONObject();
            categoryList.put("categoryList", array);
            return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, PinConstants.ResponseMessage.SUCCESS, categoryList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseWrapper.wrap(PinConstants.StatusCode.INTERNAL_ERROR, e.getMessage(), null);
        }
    }

    @PutMapping("/update-category")
    public JSONObject updateProductCategory(@RequestBody JSONObject requestJSON) {
        try {
            Integer productId = requestJSON.getInteger("productId");
            Integer categoryId = requestJSON.getInteger("categoryId");
            productService.updateProductCategory(productId, categoryId);
            return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, PinConstants.ResponseMessage.SUCCESS, null);
        } catch (Exception e) {
            return ResponseWrapper.wrap(PinConstants.StatusCode.INTERNAL_ERROR, e.getMessage(), null);
        }
    }

    @PostMapping("/create-product")
    public JSONObject createProduct(HttpServletRequest req, @RequestBody JSONObject requestJSON) {
        try {
            Integer storeId = Integer.parseInt(req.getHeader("Current-Store"));
            String url = requestJSON.getString("imageUrls");
            //String url = ImgUtil.upload(base64Img, "https://sm.ms/api/upload").getBody().getJSONObject("data").getString("url");
            String name = requestJSON.getString("name");
            String info = requestJSON.getString("info");
            String keyword = requestJSON.getString("keyword");
            BigDecimal price = new BigDecimal(requestJSON.getString("price"));
            BigDecimal priceBeforeDiscount = new BigDecimal(requestJSON.getString("priceBeforeDiscount"));
            String unitName = requestJSON.getString("unitName");
            Integer stockCount = requestJSON.getInteger("stockCount");
            BigDecimal shippingFee = new BigDecimal(requestJSON.getString("shippingFee"));
            Integer creditToGive = requestJSON.getInteger("creditToGive");
            BigDecimal cost = new BigDecimal(requestJSON.getString("cost"));
            String description = requestJSON.getString("description");

            PinProduct pinProduct = new PinProduct(storeId, 3, url, name,
                    info, keyword, price, priceBeforeDiscount, unitName, stockCount,
                    0, false, false, true, shippingFee,
                    false, new Date(), creditToGive, cost, 0, description);
            productService.save(pinProduct);
            return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, PinConstants.ResponseMessage.SUCCESS, pinProduct);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseWrapper.wrap(PinConstants.StatusCode.INTERNAL_ERROR, e.getMessage(), null);
        }
    }

    @PostMapping("/create-sku-definition")
    public JSONObject createSkuDefinition(@RequestBody JSONObject requestJSON) {
        try {
            Integer productId = requestJSON.getInteger("productId");
            String property;
            String value;
            PinProductAttributeDefinition attributeDefinition;
            for (int i = 0; i < requestJSON.getJSONArray("attribute").size(); i++) {
                property = requestJSON.getJSONArray("attribute").getJSONObject(i).getString("attributeName");
                value = requestJSON.getJSONArray("attribute").getJSONObject(i).getString("attributeValue");
                attributeDefinition = new PinProductAttributeDefinition(productId, property, value);
                definitionService.save(attributeDefinition);
            }
            return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, PinConstants.ResponseMessage.SUCCESS, null);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseWrapper.wrap(PinConstants.StatusCode.INTERNAL_ERROR, e.getMessage(), null);
        }
    }

    @PostMapping("/create-sku")
    public JSONObject createSku(@RequestBody JSONObject requestJSON) {
        try {
            Integer storeId = requestJSON.getInteger("productId");
            JSONObject object;
            String sku;
            Integer stock;
            BigDecimal price;
            String imageUrl;
            BigDecimal cost;
            PinProductAttributeValue attributeValue;
            for (int i = 0; i < requestJSON.getJSONArray("list").size(); i++) {
                object = requestJSON.getJSONArray("list").getJSONObject(i);
                sku = object.getString("sku");
                stock = object.getInteger("stock");
                price = new BigDecimal(object.getString("price"));
                //base64Img = object.getString("image");
                //imageUrl = ImgUtil.upload(base64Img, "https://sm.ms/api/upload").getBody().getJSONObject("data").getString("url");
                imageUrl = "https://i.loli.net/2019/07/05/5d1ec92eac6cb47889.png";
                cost = new BigDecimal(object.getString("cost"));
                attributeValue = new PinProductAttributeValue(storeId, sku, stock, price, imageUrl, cost);
                valueService.save(attributeValue);
            }
            return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, PinConstants.ResponseMessage.SUCCESS, "创建成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseWrapper.wrap(PinConstants.StatusCode.INTERNAL_ERROR, e.getMessage(), null);
        }
    }

    @PutMapping("/is-shown")
    public JSONObject updateProductIsShownStatus(@RequestBody JSONObject requestJSON) {
        try {
            Integer productId = requestJSON.getInteger("productId");
            Integer choice = requestJSON.getInteger("choice");
            if(choice == 0){
                productService.updateProductIsShownStatus(productId);
            } else {
                productService.updateProductIsNotShownStatus(productId);
            }
            return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, PinConstants.ResponseMessage.SUCCESS, null);
        } catch (Exception e) {
            return ResponseWrapper.wrap(PinConstants.StatusCode.INTERNAL_ERROR, e.getMessage(), null);
        }
    }

    @PostMapping("/rich-text")
    public JSONObject getProductRichText(@RequestBody JSONObject requestJSON) {
        try{
            Integer productId = requestJSON.getInteger("productId");
            String richText = requestJSON.getString("richText");
            productService.updateProductRichTextDescription(productId, richText);
            return ResponseWrapper.wrap(PinConstants.StatusCode.SUCCESS, PinConstants.ResponseMessage.SUCCESS, null);
        } catch (Exception e) {
            return ResponseWrapper.wrap(PinConstants.StatusCode.INTERNAL_ERROR, e.getMessage(), null);
        }
    }
}
