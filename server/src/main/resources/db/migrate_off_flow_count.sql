-- Off-Flow 进入次数（按触发站累计）
ALTER TABLE mes_lot
    ADD COLUMN off_flow_counts VARCHAR(512) NULL COMMENT '按触发站累计Off-Flow次数JSON' AFTER off_flow_anchor_status;
