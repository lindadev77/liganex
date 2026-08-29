package tech.liganex.studio.module.openapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 应用月度调用配额用量。复合主键 (app_id, period)，period 形如 YYYY-MM。
 */
@Data
@TableName("quota_usage")
public class QuotaUsage {

    @TableId(value = "app_id", type = IdType.INPUT)
    private String appId;

    private String period;

    private Long used;

    private Instant updatedAt;
}
