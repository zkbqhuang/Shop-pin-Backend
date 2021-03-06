package cn.edu.neu.shop.pin.schedule;

import cn.edu.neu.shop.pin.model.PinOrderGroup;
import cn.edu.neu.shop.pin.model.PinOrderIndividual;
import cn.edu.neu.shop.pin.service.OrderGroupService;
import cn.edu.neu.shop.pin.service.OrderIndividualService;
import cn.edu.neu.shop.pin.service.message.TemplateMessageService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author LLG
 * 团单定时关闭器
 * 关闭已经结束的团单
 */
@Slf4j
@Component
public class GroupClosingScheduler {

    private static Logger logger = LoggerFactory.getLogger(GroupClosingScheduler.class);

    private final OrderGroupService orderGroupService;

    private final OrderIndividualService orderIndividualService;

    @Autowired
    private TemplateMessageService templateMessageService;

    private ExecutorService executorService = Executors.newCachedThreadPool();

    public GroupClosingScheduler(OrderGroupService orderGroupService, OrderIndividualService orderIndividualService) {
        this.orderGroupService = orderGroupService;
        this.orderIndividualService = orderIndividualService;
    }

    /**
     * cron字符串决定看门狗每1分钟执行一次清理任务
     */
    @Scheduled(cron = "0 0/1 * * * ? ")
    public void closeTimeoutOrderGroup() {
        List<PinOrderGroup> orderGroups = orderGroupService.getOrdersByStatus(PinOrderGroup.STATUS_PINGING);
        final Date currentDate = new Date();
        logger.info("触发收团定时任务。");
        orderGroups.forEach(orderGroup -> executorService.submit(() -> {
            // 并发更新团单状态
            if (orderGroup.getCloseTime().compareTo(currentDate) <= 0) {
                finishGroupOrder(orderGroup);
            }
        }));
    }

    /**
     * 关闭指定的团单（设置为已结束，并标记结束时间）
     *
     * @param orderGroup 团单实体
     */
    private void finishGroupOrder(PinOrderGroup orderGroup) {
        logger.info("结束已达到完成时间的团单 #" + orderGroup.getId());
        List<PinOrderIndividual> orderIndividuals = orderIndividualService.getOrderIndividualsByOrderGroupId(orderGroup.getId());
        // 计算团单内最终总价
        BigDecimal finalAmountOfMoney = new BigDecimal(0);
        Integer peopleCount = orderIndividuals.size();
        for (PinOrderIndividual orderIndividual : orderIndividuals) {
            if (!orderIndividual.getPaid()) {
                logger.error("异常团单 #" + orderGroup.getId() + "。团单中存在未支付的订单");
                continue;
            }
            finalAmountOfMoney = finalAmountOfMoney.add(orderIndividual.getTotalPrice());
            // 发送模板消息
            templateMessageService.sendGroupSuccessfullyClosedMessageToIndividualOrderOwner(peopleCount, orderIndividual, orderGroup);
        }
        orderGroup.setTotalAmountOfMoneyPaid(finalAmountOfMoney);
        orderGroup.setActualFinishTime(new Date());
        orderGroup.setStatus(PinOrderGroup.STATUS_FINISHED);
        orderGroupService.update(orderGroup);
    }
}
