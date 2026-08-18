-- Stage 3 动态封路需要明确的 BLOCKED 状态；历史迁移保持不变。
ALTER TABLE route_edge DROP CONSTRAINT route_edge_status_check;
ALTER TABLE route_edge
    ADD CONSTRAINT route_edge_status_check
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'CLOSED', 'BLOCKED'));
