-- ============================================
-- Application Database
-- ============================================
CREATE DATABASE IF NOT EXISTS testdatagen
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- JPA ddl-auto=update will create tables automatically for this DB.

-- ============================================
-- Demo Database (for testing data generation)
-- ============================================
CREATE DATABASE IF NOT EXISTS demo_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE demo_db;

-- Grant access to app user
GRANT ALL PRIVILEGES ON demo_db.* TO 'testdatagen'@'%';
FLUSH PRIVILEGES;

-- ------------------------------------------------------------
-- 单一客户标识信息表
-- ------------------------------------------------------------
create table t_customer_identity
(
    cust_no          varchar(32)                         not null comment '客户号',
    cust_type        varchar(16)                         not null comment '客户类型: 1企业 2个人 3商户 4机构',
    cust_name        varchar(256)                        not null comment '客户名称',
    cust_short_name  varchar(128)                        null comment '客户简称',
    unified_code     varchar(32)                         null comment '统一社会信用代码',
    parent_cust_no   varchar(32)                         null comment '上级客户号(集团客户)',
    cust_level       char      default '3'               null comment '客户等级: 1战略 2核心 3普通 4小微',
    cust_status      char      default '1'               null comment '客户状态: 1正常 2潜在 3流失 4黑名单',
    belong_org       varchar(32)                         null comment '归属机构',
    belong_mgr       varchar(32)                         null comment '归属客户经理',
    open_date        date                                null comment '开户日期',
    first_trade_date date                                null comment '首笔交易日期',
    total_assets     decimal(18, 2)                      null comment '总资产(万元)',
    annual_income    decimal(18, 2)                      null comment '年收入(万元)',
    risk_level       char      default '2'               null comment '风险等级: 1低 2中低 3中 4中高 5高',
    credit_level     varchar(8)                          null comment '信用等级: AAA/AA/A/BBB/BB/B/C',
    source_channel   varchar(32)                         null comment '来源渠道',
    data_source      varchar(32)                         null comment '数据来源',
    create_time      timestamp default CURRENT_TIMESTAMP null comment '创建时间',
    update_time      timestamp default CURRENT_TIMESTAMP null comment '更新时间',
    constraint uk_unified_code
        unique (unified_code)
)
    comment '单一客户标识信息表';

-- ------------------------------------------------------------
-- 工商企业基本信息表
-- ------------------------------------------------------------
create table t_enterprise_base
(
    ent_id           varchar(32)                           not null comment '企业ID',
    unified_code     varchar(32)                           not null comment '统一社会信用代码',
    ent_name         varchar(256)                          not null comment '企业全称',
    ent_short_name   varchar(128)                          null comment '企业简称',
    ent_type_code    varchar(32)                           null comment '企业类型编码(对齐t_upstream_code.code_key)',
    ent_type_name    varchar(64)                           null comment '企业类型名称',
    reg_type_code    varchar(32)                           null comment '注册类型编码(对齐t_upstream_code.code_key)',
    reg_type_name    varchar(64)                           null comment '注册类型名称',
    reg_capital      decimal(18, 4)                        null comment '注册资本(万元)',
    paid_capital     decimal(18, 4)                        null comment '实缴资本(万元)',
    currency_code    varchar(8)  default 'CNY'             null comment '币种',
    establish_date   date                                  null comment '成立日期',
    business_status  varchar(16)                           null comment '经营状态: 1存续 2在业 3吊销 4注销 5迁出',
    reg_address      varchar(512)                          null comment '注册地址',
    business_address varchar(512)                          null comment '经营地址',
    province_code    varchar(128)                          null comment '省份编码(对齐t_gs_key.key_value)',
    province_name    varchar(64)                           null comment '省份名称',
    city_code        varchar(128)                          null comment '城市编码(对齐t_gs_key.key_value)',
    city_name        varchar(64)                           null comment '城市名称',
    district_code    varchar(8)                            null comment '区县编码',
    district_name    varchar(64)                           null comment '区县名称',
    longitude        decimal(10, 6)                        null comment '经度',
    latitude         decimal(10, 6)                        null comment '纬度',
    legal_name       varchar(128)                          null comment '法人姓名',
    legal_cert_no    varchar(64)                           null comment '法人证件号',
    legal_phone      varchar(32)                           null comment '法人电话',
    legal_email      varchar(128)                          null comment '法人邮箱',
    contact_name     varchar(128)                          null comment '联系人姓名',
    contact_phone    varchar(32)                           null comment '联系人电话',
    contact_email    varchar(128)                          null comment '联系人邮箱',
    business_scope   text                                  null comment '经营范围',
    industry_code    varchar(32)                           null comment '行业分类编码(对齐t_external_code.code_item)',
    industry_name    varchar(128)                          null comment '行业分类名称',
    reg_org          varchar(128)                          null comment '登记机关',
    approval_date    date                                  null comment '核准日期',
    biz_term_from    date                                  null comment '营业期限起始',
    biz_term_to      date                                  null comment '营业期限截止',
    staff_count      int                                   null comment '员工人数',
    annual_revenue   decimal(18, 2)                        null comment '年营业额(万元)',
    tax_no           varchar(32)                           null comment '纳税人识别号',
    tax_type         varchar(16)                           null comment '纳税类型: 1一般纳税人 2小规模纳税人',
    ent_scale        varchar(32)                           null comment '企业规模(对齐t_upstream_code.code_key): 1大型 2中型 3小型 4微型',
    hi_tech_flag     char        default '0'               null comment '高新技术企业标志: 1是 0否',
    ie_right_flag    char        default '0'               null comment '进出口权标志: 1有 0无',
    listed_flag      char        default '0'               null comment '是否上市: 1是 0否',
    listed_code      varchar(16)                           null comment '上市代码',
    website          varchar(256)                          null comment '企业网站',
    stockholder_info varchar(1024)                         null comment '主要股东信息',
    data_source      varchar(32)                           null comment '数据来源',
    data_version     varchar(16) default '1.0'             null comment '数据版本',
    create_time      timestamp   default CURRENT_TIMESTAMP null comment '创建时间',
    update_time      timestamp   default CURRENT_TIMESTAMP null comment '更新时间',
    constraint uk_unified_code
        unique (unified_code)
)
    comment '工商企业基本信息表';

-- ------------------------------------------------------------
-- 外部数据码值表
-- ------------------------------------------------------------
create table t_external_code
(
    ext_code_id      varchar(32)                           not null comment '外部码值ID',
    data_source      varchar(64)                           not null comment '数据源标识',
    category_code    varchar(64)                           not null comment '分类编码',
    category_name    varchar(128)                          not null comment '分类名称',
    code_item        varchar(32)                           not null comment '码值项',
    code_item_name   varchar(256)                          not null comment '码值项名称',
    code_item_alias  varchar(256)                          null comment '码值别名',
    code_level       int         default 1                 null comment '码值层级',
    parent_item      varchar(32)                           null comment '父级码值项',
    mapping_standard varchar(64)                           null comment '映射标准',
    mapping_code     varchar(32)                           null comment '映射码值',
    version_no       varchar(16) default '1.0'             null comment '版本号',
    status           char        default '1'               null comment '状态: 1有效 0无效',
    publish_date     date                                  null comment '发布日期',
    sync_time        timestamp   default CURRENT_TIMESTAMP null comment '同步时间',
    data_quality     char        default 'A'               null comment '数据质量等级: A/B/C/D'
)
    comment '外部数据码值表';

-- ------------------------------------------------------------
-- 全量工商企业信息表
-- ------------------------------------------------------------
create table t_full_enterprise_info
(
    record_id          varchar(32)                         not null comment '记录ID',
    cust_no            varchar(32)                         null comment '客户号',
    ent_id             varchar(32)                         null comment '企业ID',
    merchant_id        varchar(32)                         null comment '商户ID',
    unified_code       varchar(32)                         null comment '统一社会信用代码',
    credit_code_status varchar(16)                         null comment '信用代码状态',
    ent_name           varchar(256)                        null comment '企业全称',
    ent_short_name     varchar(128)                        null comment '企业简称',
    merchant_name      varchar(256)                        null comment '商户名称',
    cust_name          varchar(128)                        null comment '客户名称',
    ent_type_code      varchar(32)                         null comment '企业类型编码',
    ent_type_name      varchar(64)                         null comment '企业类型名称',
    reg_type_code      varchar(32)                         null comment '注册类型编码',
    reg_type_name      varchar(64)                         null comment '注册类型名称',
    industry_code      varchar(32)                         null comment '行业分类编码',
    industry_name      varchar(128)                        null comment '行业分类名称',
    biz_category       varchar(32)                         null comment '经营类目编码',
    biz_category_name  varchar(128)                        null comment '经营类目名称',
    reg_capital        decimal(18, 4)                      null comment '注册资本(万元)',
    paid_capital       decimal(18, 4)                      null comment '实缴资本(万元)',
    currency_code      varchar(8)                          null comment '币种',
    ent_scale          varchar(32)                         null comment '企业规模',
    establish_date     date                                null comment '成立日期',
    biz_term_from      date                                null comment '营业期限起始',
    biz_term_to        date                                null comment '营业期限截止',
    biz_age_years      int                                 null comment '经营年限(年)',
    business_status    varchar(16)                         null comment '经营状态',
    merchant_status    char                                null comment '商户状态',
    cust_status        char                                null comment '客户状态',
    overall_status     varchar(16)                         null comment '综合状态',
    reg_address        varchar(512)                        null comment '注册地址',
    business_address   varchar(512)                        null comment '经营地址',
    actual_address     varchar(512)                        null comment '实际经营地址',
    province_code      varchar(128)                        null comment '省份编码',
    province_name      varchar(64)                         null comment '省份名称',
    city_code          varchar(128)                        null comment '城市编码',
    city_name          varchar(64)                         null comment '城市名称',
    district_code      varchar(8)                          null comment '区县编码',
    district_name      varchar(64)                         null comment '区县名称',
    longitude          decimal(10, 6)                      null comment '经度',
    latitude           decimal(10, 6)                      null comment '纬度',
    legal_name         varchar(128)                        null comment '法人姓名',
    legal_cert_no      varchar(64)                         null comment '法人证件号',
    legal_phone        varchar(32)                         null comment '法人电话',
    legal_email        varchar(128)                        null comment '法人邮箱',
    legal_nationality  varchar(8)                          null comment '法人国籍',
    legal_gender       char                                null comment '法人性别',
    contact_name       varchar(128)                        null comment '联系人姓名',
    contact_phone      varchar(32)                         null comment '联系人电话',
    contact_email      varchar(128)                        null comment '联系人邮箱',
    contact_address    varchar(512)                        null comment '联系人地址',
    business_scope     text                                null comment '经营范围',
    reg_org            varchar(128)                        null comment '登记机关',
    approval_date      date                                null comment '核准日期',
    tax_no             varchar(32)                         null comment '纳税人识别号',
    tax_type           varchar(16)                         null comment '纳税类型',
    hi_tech_flag       char                                null comment '高新技术企业标志',
    ie_right_flag      char                                null comment '进出口权标志',
    listed_flag        char                                null comment '是否上市',
    listed_code        varchar(16)                         null comment '上市代码',
    website            varchar(256)                        null comment '企业网站',
    ent_staff_count    int                                 null comment '企业员工数',
    merchant_staff     int                                 null comment '商户员工数',
    total_staff        int                                 null comment '总员工数',
    annual_revenue     decimal(18, 2)                      null comment '年营业额(万元)',
    avg_daily_turnover decimal(18, 2)                      null comment '日均营业额',
    monthly_turnover   decimal(18, 2)                      null comment '月营业额',
    total_assets       decimal(18, 2)                      null comment '总资产(万元)',
    annual_income      decimal(18, 2)                      null comment '年收入(万元)',
    cust_total_assets  decimal(18, 2)                      null comment '客户总资产(万元)',
    settle_account     varchar(32)                         null comment '结算账户',
    settle_bank        varchar(128)                        null comment '开户行',
    settle_bank_code   varchar(16)                         null comment '开户行行号',
    settle_cycle       varchar(8)                          null comment '结算周期',
    settle_rate        decimal(6, 4)                       null comment '结算费率',
    deposit_amount     decimal(18, 2)                      null comment '保证金金额',
    risk_level         char                                null comment '风险等级',
    credit_score       int                                 null comment '信用评分',
    merchant_level     varchar(8)                          null comment '商户等级',
    cust_credit_level  varchar(8)                          null comment '客户信用等级',
    credit_level       varchar(8)                          null comment '综合信用等级',
    brand_name         varchar(128)                        null comment '品牌名称',
    chain_flag         char                                null comment '是否连锁',
    store_count        int                                 null comment '门店数量',
    biz_area           decimal(8, 2)                       null comment '经营面积(m2)',
    cust_source        varchar(32)                         null comment '客户来源渠道',
    data_source        varchar(32)                         null comment '主数据来源',
    data_version       varchar(16)                         null comment '数据版本',
    data_quality       char                                null comment '数据质量等级',
    create_time        timestamp default CURRENT_TIMESTAMP null comment '创建时间',
    update_time        timestamp default CURRENT_TIMESTAMP null comment '更新时间',
    etl_date           date                                null comment 'ETL日期'
)
    comment '全量工商企业信息表';

-- ------------------------------------------------------------
-- 工商key表
-- ------------------------------------------------------------
create table t_gs_key
(
    key_id           varchar(32)      not null comment 'Key ID',
    key_type         varchar(32)      not null comment 'Key类型: CREDIT_CODE/REG_NO/ORG_CODE',
    key_value        varchar(128)     not null comment 'Key值',
    key_name         varchar(128)     not null comment 'Key名称',
    standard_code    varchar(64)      null comment '对应标准编码',
    standard_name    varchar(128)     null comment '对应标准名称',
    province_code    varchar(8)       null comment '省份编码',
    province_name    varchar(64)      null comment '省份名称',
    city_code        varchar(8)       null comment '城市编码',
    city_name        varchar(64)      null comment '城市名称',
    valid_status     char default '1' null comment '有效状态',
    create_date      date             null comment '创建日期',
    last_verify_date date             null comment '最后核验日期'
)
    comment '工商key表';

-- ------------------------------------------------------------
-- 法人客户证件信息表
-- ------------------------------------------------------------
create table t_legal_person_cert
(
    cert_id         varchar(32)                          not null comment '证件记录ID',
    cust_no         varchar(32)                          not null comment '客户号',
    cert_type       varchar(16)                          not null comment '证件类型: ID-身份证 BL-营业执照 ORG-组织机构代码',
    cert_no         varchar(64)                          not null comment '证件号码',
    cert_name       varchar(128)                         not null comment '证件名称(法人姓名或企业名称)',
    issue_org       varchar(128)                         null comment '发证机关',
    issue_date      date                                 null comment '发证日期',
    valid_from      date                                 null comment '有效期起始',
    valid_to        date                                 null comment '有效期截止',
    cert_status     char       default '1'               null comment '证件状态: 1有效 2过期 3吊销 4冻结',
    verify_status   char       default '0'               null comment '核验状态: 0未核验 1已核验 2核验失败',
    verify_time     timestamp                            null comment '核验时间',
    cert_image_url  varchar(512)                         null comment '证件影像URL',
    cert_hash       varchar(64)                          null comment '证件哈希值',
    nationality     varchar(8) default 'CN'              null comment '国籍',
    gender          char                                 null comment '性别: M男 F女',
    birth_date      date                                 null comment '出生日期',
    reg_address     varchar(512)                         null comment '户籍/注册地址',
    id_card_address varchar(512)                         null comment '身份证地址',
    create_time     timestamp  default CURRENT_TIMESTAMP null comment '创建时间',
    update_time     timestamp  default CURRENT_TIMESTAMP null comment '更新时间',
    constraint uk_cert_no_type
        unique (cert_no, cert_type)
)
    comment '法人客户证件信息表';

-- ------------------------------------------------------------
-- 法人商户基本信息表
-- ------------------------------------------------------------
create table t_merchant_base
(
    merchant_id        varchar(32)                         not null comment '商户ID',
    ent_id             varchar(36)                         not null comment '企业ID(关联t_enterprise_base)',
    cust_no            varchar(32)                         not null comment '客户号(关联t_customer_identity)',
    merchant_name      varchar(256)                        not null comment '商户名称',
    merchant_short     varchar(128)                        null comment '商户简称',
    merchant_type      varchar(16)                         null comment '商户类型: 1实体 2电商 3连锁 4个体工商户',
    biz_category       varchar(32)                         null comment '经营类目编码',
    biz_category_name  varchar(128)                        null comment '经营类目名称',
    settle_account     varchar(32)                         null comment '结算账户',
    settle_bank        varchar(128)                        null comment '开户行',
    settle_bank_code   varchar(16)                         null comment '开户行行号',
    settle_cycle       varchar(8)                          null comment '结算周期: T0/T1/T7/T30',
    settle_rate        decimal(6, 4)                       null comment '结算费率',
    merchant_status    char      default '1'               null comment '商户状态: 1正常 2待审核 3暂停 4注销',
    sign_date          date                                null comment '签约日期',
    expiry_date        date                                null comment '到期日期',
    contact_name       varchar(128)                        null comment '联系人',
    contact_phone      varchar(32)                         null comment '联系电话',
    contact_email      varchar(128)                        null comment '联系邮箱',
    contact_address    varchar(512)                        null comment '联系地址',
    risk_level         char      default '2'               null comment '风险等级: 1低 2中 3高',
    credit_score       int                                 null comment '信用评分(0-1000)',
    merchant_level     varchar(8)                          null comment '商户等级: S/A/B/C/D',
    deposit_amount     decimal(18, 2)                      null comment '保证金金额',
    actual_address     varchar(512)                        null comment '实际经营地址',
    biz_area           decimal(8, 2)                       null comment '经营面积(m2)',
    staff_count        int                                 null comment '员工数',
    brand_name         varchar(128)                        null comment '品牌名称',
    chain_flag         char      default '0'               null comment '是否连锁: 1是 0否',
    store_count        int       default 1                 null comment '门店数量',
    avg_daily_turnover decimal(18, 2)                      null comment '日均营业额',
    monthly_turnover   decimal(18, 2)                      null comment '月营业额',
    data_source        varchar(32)                         null comment '数据来源',
    create_time        timestamp default CURRENT_TIMESTAMP null comment '创建时间',
    update_time        timestamp default CURRENT_TIMESTAMP null comment '更新时间'
)
    comment '法人商户基本信息表';

-- ------------------------------------------------------------
-- 上游码值表
-- ------------------------------------------------------------
create table t_upstream_code
(
    code_id        varchar(32)                         not null comment '码值ID',
    code_type      varchar(64)                         not null comment '码值类型',
    code_type_name varchar(128)                        not null comment '码值类型名称',
    code_key       varchar(36)                         not null comment '码值键',
    code_value     varchar(256)                        not null comment '码值名称',
    code_desc      varchar(512)                        null comment '码值描述',
    parent_code_id varchar(32)                         null comment '上级码值ID',
    sort_order     int       default 0                 null comment '排序号',
    valid_flag     char      default '1'               null comment '有效标志: 1有效 0无效',
    effective_date date                                null comment '生效日期',
    expiry_date    date                                null comment '失效日期',
    source_system  varchar(64)                         null comment '来源系统',
    create_time    timestamp default CURRENT_TIMESTAMP null comment '创建时间',
    update_time    timestamp default CURRENT_TIMESTAMP null comment '更新时间',
    remark         varchar(512)                        null comment '备注'
)
    comment '上游码值表';
