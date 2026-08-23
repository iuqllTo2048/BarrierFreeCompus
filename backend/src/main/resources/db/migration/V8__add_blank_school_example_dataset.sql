INSERT INTO campus(id, code, name, description, center_lng, center_lat, coordinate_system)
VALUES (
    '10000000-0000-0000-0000-000000000002',
    'SCHOOL_EXAMPLE',
    '学校示例校园',
    '由管理员在真实高德底图上手工建设的空白校园',
    104.695359,
    31.534827,
    'GCJ02'
);

INSERT INTO dataset(
    id, campus_id, code, name, dataset_type, coordinate_system,
    enabled, is_demo, seed, description
)
VALUES (
    '20000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000002',
    'SCHOOL_EXAMPLE_V1',
    '学校示例校园数据集',
    'FORMAL',
    'GCJ02',
    TRUE,
    FALSE,
    NULL,
    '空白正式数据集；节点、道路、设施和障碍均由管理员手工标注'
);

UPDATE dataset
SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP
WHERE code = 'YUNLU_DEMO_V1';
