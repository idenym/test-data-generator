-- ============================================================
-- 复杂业务场景测试：全量工商企业信息表数据加工
-- 场景说明：基于6张来源表构建一张全量工商企业信息宽表
-- 用于验证数据生成工具对多表关联、数据转换、条件筛选的支持能力
-- ============================================================

-- ------------------------------------------------------------
-- 来源表1: 上游码值表 (t_upstream_code)
-- 说明：上游系统提供的标准码值映射，如企业类型、经营状态等
-- ------------------------------------------------------------
CREATE TABLE t_upstream_code (
    code_id          VARCHAR(32)  NOT NULL COMMENT '码值ID',
    code_type        VARCHAR(64)  NOT NULL COMMENT '码值类型',
    code_type_name   VARCHAR(128) NOT NULL COMMENT '码值类型名称',
    code_key         VARCHAR(32)  NOT NULL COMMENT '码值键',
    code_value       VARCHAR(256) NOT NULL COMMENT '码值名称',
    code_desc        VARCHAR(512) COMMENT '码值描述',
    parent_code_id   VARCHAR(32) COMMENT '上级码值ID',
    sort_order       INT DEFAULT 0 COMMENT '排序号',
    valid_flag       CHAR(1) DEFAULT '1' COMMENT '有效标志: 1有效 0无效',
    effective_date   DATE COMMENT '生效日期',
    expiry_date      DATE COMMENT '失效日期',
    source_system    VARCHAR(64) COMMENT '来源系统',
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    remark           VARCHAR(512) COMMENT '备注'
) COMMENT '上游码值表';

-- ------------------------------------------------------------
-- 来源表2: 外部数据码值表 (t_external_code)
-- 说明：外部数据源提供的码值映射，如行业分类、地区编码等
-- ------------------------------------------------------------
CREATE TABLE t_external_code (
    ext_code_id      VARCHAR(32)  NOT NULL COMMENT '外部码值ID',
    data_source      VARCHAR(64)  NOT NULL COMMENT '数据源标识',
    category_code    VARCHAR(64)  NOT NULL COMMENT '分类编码',
    category_name    VARCHAR(128) NOT NULL COMMENT '分类名称',
    code_item        VARCHAR(32)  NOT NULL COMMENT '码值项',
    code_item_name   VARCHAR(256) NOT NULL COMMENT '码值项名称',
    code_item_alias  VARCHAR(256) COMMENT '码值别名',
    code_level       INT DEFAULT 1 COMMENT '码值层级',
    parent_item      VARCHAR(32) COMMENT '父级码值项',
    mapping_standard VARCHAR(64) COMMENT '映射标准',
    mapping_code     VARCHAR(32) COMMENT '映射码值',
    version_no       VARCHAR(16) DEFAULT '1.0' COMMENT '版本号',
    status           CHAR(1) DEFAULT '1' COMMENT '状态: 1有效 0无效',
    publish_date     DATE COMMENT '发布日期',
    sync_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '同步时间',
    data_quality     CHAR(1) DEFAULT 'A' COMMENT '数据质量等级: A/B/C/D'
) COMMENT '外部数据码值表';

-- ------------------------------------------------------------
-- 来源表3: 工商key表 (t_gs_key)
-- 说明：工商系统的关键字段映射，如统一社会信用代码规则、注册号规则等
-- ------------------------------------------------------------
CREATE TABLE t_gs_key (
    key_id           VARCHAR(32)  NOT NULL COMMENT 'Key ID',
    key_type         VARCHAR(32)  NOT NULL COMMENT 'Key类型: CREDIT_CODE/REG_NO/ORG_CODE',
    key_value        VARCHAR(128) NOT NULL COMMENT 'Key值',
    key_name         VARCHAR(128) NOT NULL COMMENT 'Key名称',
    standard_code    VARCHAR(64)  COMMENT '对应标准编码',
    standard_name    VARCHAR(128) COMMENT '对应标准名称',
    province_code    VARCHAR(8)   COMMENT '省份编码',
    province_name    VARCHAR(64)  COMMENT '省份名称',
    city_code        VARCHAR(8)   COMMENT '城市编码',
    city_name        VARCHAR(64)  COMMENT '城市名称',
    valid_status     CHAR(1) DEFAULT '1' COMMENT '有效状态',
    create_date      DATE COMMENT '创建日期',
    last_verify_date DATE COMMENT '最后核验日期'
) COMMENT '工商key表';

-- ------------------------------------------------------------
-- 来源表4: 法人客户证件信息表 (t_legal_person_cert)
-- 说明：法人及企业相关证件信息
-- ------------------------------------------------------------
CREATE TABLE t_legal_person_cert (
    cert_id          VARCHAR(32)  NOT NULL COMMENT '证件记录ID',
    cust_no          VARCHAR(32)  NOT NULL COMMENT '客户号',
    cert_type        VARCHAR(16)  NOT NULL COMMENT '证件类型: ID-身份证 BL-营业执照 ORG-组织机构代码',
    cert_no          VARCHAR(64)  NOT NULL COMMENT '证件号码',
    cert_name        VARCHAR(128) NOT NULL COMMENT '证件名称(法人姓名或企业名称)',
    issue_org        VARCHAR(128) COMMENT '发证机关',
    issue_date       DATE COMMENT '发证日期',
    valid_from       DATE COMMENT '有效期起始',
    valid_to         DATE COMMENT '有效期截止',
    cert_status      CHAR(1) DEFAULT '1' COMMENT '证件状态: 1有效 2过期 3吊销 4冻结',
    verify_status    CHAR(1) DEFAULT '0' COMMENT '核验状态: 0未核验 1已核验 2核验失败',
    verify_time      TIMESTAMP COMMENT '核验时间',
    cert_image_url   VARCHAR(512) COMMENT '证件影像URL',
    cert_hash        VARCHAR(64) COMMENT '证件哈希值',
    nationality      VARCHAR(8) DEFAULT 'CN' COMMENT '国籍',
    gender           CHAR(1) COMMENT '性别: M男 F女',
    birth_date       DATE COMMENT '出生日期',
    reg_address      VARCHAR(512) COMMENT '户籍/注册地址',
    id_card_address  VARCHAR(512) COMMENT '身份证地址',
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_cert_no_type (cert_no, cert_type)
) COMMENT '法人客户证件信息表';

-- ------------------------------------------------------------
-- 来源表5: 单一客户标识信息表 (t_customer_identity)
-- 说明：客户唯一标识体系，统一客户识别
-- ------------------------------------------------------------
CREATE TABLE t_customer_identity (
    cust_no          VARCHAR(32)  NOT NULL COMMENT '客户号',
    cust_type        VARCHAR(16)  NOT NULL COMMENT '客户类型: 1企业 2个人 3商户 4机构',
    cust_name        VARCHAR(256) NOT NULL COMMENT '客户名称',
    cust_short_name  VARCHAR(128) COMMENT '客户简称',
    unified_code     VARCHAR(32)  COMMENT '统一社会信用代码',
    parent_cust_no   VARCHAR(32) COMMENT '上级客户号(集团客户)',
    cust_level       CHAR(1) DEFAULT '3' COMMENT '客户等级: 1战略 2核心 3普通 4小微',
    cust_status      CHAR(1) DEFAULT '1' COMMENT '客户状态: 1正常 2潜在 3流失 4黑名单',
    belong_org       VARCHAR(32) COMMENT '归属机构',
    belong_mgr       VARCHAR(32) COMMENT '归属客户经理',
    open_date        DATE COMMENT '开户日期',
    first_trade_date DATE COMMENT '首笔交易日期',
    total_assets     DECIMAL(18,2) COMMENT '总资产(万元)',
    annual_income    DECIMAL(18,2) COMMENT '年收入(万元)',
    risk_level       CHAR(1) DEFAULT '2' COMMENT '风险等级: 1低 2中低 3中 4中高 5高',
    credit_level     VARCHAR(8) COMMENT '信用等级: AAA/AA/A/BBB/BB/B/C',
    source_channel   VARCHAR(32) COMMENT '来源渠道',
    data_source      VARCHAR(32) COMMENT '数据来源',
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_unified_code (unified_code)
) COMMENT '单一客户标识信息表';

-- ------------------------------------------------------------
-- 来源表6: 工商企业基本信息表 (t_enterprise_base)
-- 说明：核心企业信息，包含工商注册、经营、股东等全方位信息
-- ------------------------------------------------------------
CREATE TABLE t_enterprise_base (
    ent_id           VARCHAR(32)  NOT NULL COMMENT '企业ID',
    unified_code     VARCHAR(32)  NOT NULL COMMENT '统一社会信用代码',
    ent_name         VARCHAR(256) NOT NULL COMMENT '企业全称',
    ent_short_name   VARCHAR(128) COMMENT '企业简称',
    ent_type_code    VARCHAR(32)  COMMENT '企业类型编码(对齐t_upstream_code.code_key)',
    ent_type_name    VARCHAR(64)  COMMENT '企业类型名称',
    reg_type_code    VARCHAR(32)  COMMENT '注册类型编码(对齐t_upstream_code.code_key)',
    reg_type_name    VARCHAR(64)  COMMENT '注册类型名称',
    reg_capital      DECIMAL(18,4) COMMENT '注册资本(万元)',
    paid_capital     DECIMAL(18,4) COMMENT '实缴资本(万元)',
    currency_code    VARCHAR(8) DEFAULT 'CNY' COMMENT '币种',
    establish_date   DATE COMMENT '成立日期',
    business_status  VARCHAR(16) COMMENT '经营状态: 1存续 2在业 3吊销 4注销 5迁出',
    reg_address      VARCHAR(512) COMMENT '注册地址',
    business_address VARCHAR(512) COMMENT '经营地址',
    province_code    VARCHAR(128) COMMENT '省份编码(对齐t_gs_key.key_value)',
    province_name    VARCHAR(64)  COMMENT '省份名称',
    city_code        VARCHAR(128) COMMENT '城市编码(对齐t_gs_key.key_value)',
    city_name        VARCHAR(64)  COMMENT '城市名称',
    district_code    VARCHAR(8)   COMMENT '区县编码',
    district_name    VARCHAR(64)  COMMENT '区县名称',
    longitude        DECIMAL(10,6) COMMENT '经度',
    latitude         DECIMAL(10,6) COMMENT '纬度',
    legal_name       VARCHAR(128) COMMENT '法人姓名',
    legal_cert_no    VARCHAR(64)  COMMENT '法人证件号',
    legal_phone      VARCHAR(32)  COMMENT '法人电话',
    legal_email      VARCHAR(128) COMMENT '法人邮箱',
    contact_name     VARCHAR(128) COMMENT '联系人姓名',
    contact_phone    VARCHAR(32)  COMMENT '联系人电话',
    contact_email    VARCHAR(128) COMMENT '联系人邮箱',
    business_scope   TEXT COMMENT '经营范围',
    industry_code    VARCHAR(32)  COMMENT '行业分类编码(对齐t_external_code.code_item)',
    industry_name    VARCHAR(128) COMMENT '行业分类名称',
    reg_org          VARCHAR(128) COMMENT '登记机关',
    approval_date    DATE COMMENT '核准日期',
    biz_term_from    DATE COMMENT '营业期限起始',
    biz_term_to      DATE COMMENT '营业期限截止',
    staff_count      INT COMMENT '员工人数',
    annual_revenue   DECIMAL(18,2) COMMENT '年营业额(万元)',
    tax_no           VARCHAR(32)  COMMENT '纳税人识别号',
    tax_type         VARCHAR(16)  COMMENT '纳税类型: 1一般纳税人 2小规模纳税人',
    ent_scale        VARCHAR(32)  COMMENT '企业规模(对齐t_upstream_code.code_key): 1大型 2中型 3小型 4微型',
    hi_tech_flag     CHAR(1) DEFAULT '0' COMMENT '高新技术企业标志: 1是 0否',
    ie_right_flag    CHAR(1) DEFAULT '0' COMMENT '进出口权标志: 1有 0无',
    listed_flag      CHAR(1) DEFAULT '0' COMMENT '是否上市: 1是 0否',
    listed_code      VARCHAR(16)  COMMENT '上市代码',
    website          VARCHAR(256) COMMENT '企业网站',
    stockholder_info VARCHAR(1024) COMMENT '主要股东信息',
    data_source      VARCHAR(32)  COMMENT '数据来源',
    data_version     VARCHAR(16) DEFAULT '1.0' COMMENT '数据版本',
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_unified_code (unified_code)
) COMMENT '工商企业基本信息表';

-- ------------------------------------------------------------
-- 来源表7: 法人商户基本信息表 (t_merchant_base)
-- 说明：商户经营信息，包含结算、费率、风险等
-- ------------------------------------------------------------
CREATE TABLE t_merchant_base (
    merchant_id      VARCHAR(32)  NOT NULL COMMENT '商户ID',
    ent_id           VARCHAR(32)  NOT NULL COMMENT '企业ID(关联t_enterprise_base)',
    cust_no          VARCHAR(32)  NOT NULL COMMENT '客户号(关联t_customer_identity)',
    merchant_name    VARCHAR(256) NOT NULL COMMENT '商户名称',
    merchant_short   VARCHAR(128) COMMENT '商户简称',
    merchant_type    VARCHAR(16)  COMMENT '商户类型: 1实体 2电商 3连锁 4个体工商户',
    biz_category     VARCHAR(32)  COMMENT '经营类目编码',
    biz_category_name VARCHAR(128) COMMENT '经营类目名称',
    settle_account   VARCHAR(32)  COMMENT '结算账户',
    settle_bank      VARCHAR(128) COMMENT '开户行',
    settle_bank_code VARCHAR(16)  COMMENT '开户行行号',
    settle_cycle     VARCHAR(8)   COMMENT '结算周期: T0/T1/T7/T30',
    settle_rate      DECIMAL(6,4) COMMENT '结算费率',
    merchant_status  CHAR(1) DEFAULT '1' COMMENT '商户状态: 1正常 2待审核 3暂停 4注销',
    sign_date        DATE COMMENT '签约日期',
    expiry_date      DATE COMMENT '到期日期',
    contact_name     VARCHAR(128) COMMENT '联系人',
    contact_phone    VARCHAR(32)  COMMENT '联系电话',
    contact_email    VARCHAR(128) COMMENT '联系邮箱',
    contact_address  VARCHAR(512) COMMENT '联系地址',
    risk_level       CHAR(1) DEFAULT '2' COMMENT '风险等级: 1低 2中 3高',
    credit_score     INT COMMENT '信用评分(0-1000)',
    merchant_level   VARCHAR(8)   COMMENT '商户等级: S/A/B/C/D',
    deposit_amount   DECIMAL(18,2) COMMENT '保证金金额',
    actual_address   VARCHAR(512) COMMENT '实际经营地址',
    biz_area         DECIMAL(8,2) COMMENT '经营面积(m2)',
    staff_count      INT COMMENT '员工数',
    brand_name       VARCHAR(128) COMMENT '品牌名称',
    chain_flag       CHAR(1) DEFAULT '0' COMMENT '是否连锁: 1是 0否',
    store_count      INT DEFAULT 1 COMMENT '门店数量',
    avg_daily_turnover DECIMAL(18,2) COMMENT '日均营业额',
    monthly_turnover DECIMAL(18,2) COMMENT '月营业额',
    data_source      VARCHAR(32)  COMMENT '数据来源',
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
    ) COMMENT '法人商户基本信息表';

-- ------------------------------------------------------------
-- 目标表: 全量工商企业信息表 (t_full_enterprise_info)
-- 说明：由6张来源表JOIN构建的宽表，用于下游数据消费
-- ------------------------------------------------------------
CREATE TABLE t_full_enterprise_info (
    -- 主键与客户标识
    record_id        VARCHAR(32)  NOT NULL COMMENT '记录ID',
    cust_no          VARCHAR(32)  COMMENT '客户号',
    ent_id           VARCHAR(32)  COMMENT '企业ID',
    merchant_id      VARCHAR(32)  COMMENT '商户ID',

    -- 统一社会信用代码(多源融合)
    unified_code     VARCHAR(32)  COMMENT '统一社会信用代码',
    credit_code_status VARCHAR(16) COMMENT '信用代码状态',

    -- 企业名称信息
    ent_name         VARCHAR(256) COMMENT '企业全称',
    ent_short_name   VARCHAR(128) COMMENT '企业简称',
    merchant_name    VARCHAR(256) COMMENT '商户名称',
    cust_name        VARCHAR(128) COMMENT '客户名称',

    -- 企业类型与分类
    ent_type_code    VARCHAR(32)  COMMENT '企业类型编码',
    ent_type_name    VARCHAR(64)  COMMENT '企业类型名称',
    reg_type_code    VARCHAR(32)  COMMENT '注册类型编码',
    reg_type_name    VARCHAR(64)  COMMENT '注册类型名称',
    industry_code    VARCHAR(32)  COMMENT '行业分类编码',
    industry_name    VARCHAR(128) COMMENT '行业分类名称',
    biz_category     VARCHAR(32)  COMMENT '经营类目编码',
    biz_category_name VARCHAR(128) COMMENT '经营类目名称',

    -- 注册资本信息
    reg_capital      DECIMAL(18,4) COMMENT '注册资本(万元)',
    paid_capital     DECIMAL(18,4) COMMENT '实缴资本(万元)',
    currency_code    VARCHAR(8)   COMMENT '币种',
    ent_scale        VARCHAR(32)  COMMENT '企业规模',

    -- 经营时间信息
    establish_date   DATE COMMENT '成立日期',
    biz_term_from    DATE COMMENT '营业期限起始',
    biz_term_to      DATE COMMENT '营业期限截止',
    biz_age_years    INT COMMENT '经营年限(年)',

    -- 经营状态
    business_status  VARCHAR(16) COMMENT '经营状态',
    merchant_status  CHAR(1) COMMENT '商户状态',
    cust_status      CHAR(1) COMMENT '客户状态',
    overall_status   VARCHAR(16) COMMENT '综合状态',

    -- 地址信息
    reg_address      VARCHAR(512) COMMENT '注册地址',
    business_address VARCHAR(512) COMMENT '经营地址',
    actual_address   VARCHAR(512) COMMENT '实际经营地址',
    province_code    VARCHAR(128) COMMENT '省份编码',
    province_name    VARCHAR(64)  COMMENT '省份名称',
    city_code        VARCHAR(128) COMMENT '城市编码',
    city_name        VARCHAR(64)  COMMENT '城市名称',
    district_code    VARCHAR(8)   COMMENT '区县编码',
    district_name    VARCHAR(64)  COMMENT '区县名称',
    longitude        DECIMAL(10,6) COMMENT '经度',
    latitude         DECIMAL(10,6) COMMENT '纬度',

    -- 法人信息
    legal_name       VARCHAR(128) COMMENT '法人姓名',
    legal_cert_no    VARCHAR(64)  COMMENT '法人证件号',
    legal_phone      VARCHAR(32)  COMMENT '法人电话',
    legal_email      VARCHAR(128) COMMENT '法人邮箱',
    legal_nationality VARCHAR(8)  COMMENT '法人国籍',
    legal_gender     CHAR(1) COMMENT '法人性别',

    -- 联系人信息
    contact_name     VARCHAR(128) COMMENT '联系人姓名',
    contact_phone    VARCHAR(32)  COMMENT '联系人电话',
    contact_email    VARCHAR(128) COMMENT '联系人邮箱',
    contact_address  VARCHAR(512) COMMENT '联系人地址',

    -- 经营范围与资质
    business_scope   TEXT COMMENT '经营范围',
    reg_org          VARCHAR(128) COMMENT '登记机关',
    approval_date    DATE COMMENT '核准日期',
    tax_no           VARCHAR(32)  COMMENT '纳税人识别号',
    tax_type         VARCHAR(16)  COMMENT '纳税类型',
    hi_tech_flag     CHAR(1) COMMENT '高新技术企业标志',
    ie_right_flag    CHAR(1) COMMENT '进出口权标志',
    listed_flag      CHAR(1) COMMENT '是否上市',
    listed_code      VARCHAR(16)  COMMENT '上市代码',
    website          VARCHAR(256) COMMENT '企业网站',

    -- 人员规模
    ent_staff_count  INT COMMENT '企业员工数',
    merchant_staff   INT COMMENT '商户员工数',
    total_staff      INT COMMENT '总员工数',

    -- 财务信息
    annual_revenue   DECIMAL(18,2) COMMENT '年营业额(万元)',
    avg_daily_turnover DECIMAL(18,2) COMMENT '日均营业额',
    monthly_turnover DECIMAL(18,2) COMMENT '月营业额',
    total_assets     DECIMAL(18,2) COMMENT '总资产(万元)',
    annual_income    DECIMAL(18,2) COMMENT '年收入(万元)',
    cust_total_assets DECIMAL(18,2) COMMENT '客户总资产(万元)',

    -- 商户结算信息
    settle_account   VARCHAR(32)  COMMENT '结算账户',
    settle_bank      VARCHAR(128) COMMENT '开户行',
    settle_bank_code VARCHAR(16)  COMMENT '开户行行号',
    settle_cycle     VARCHAR(8)   COMMENT '结算周期',
    settle_rate      DECIMAL(6,4) COMMENT '结算费率',
    deposit_amount   DECIMAL(18,2) COMMENT '保证金金额',

    -- 风险与信用
    risk_level       CHAR(1) COMMENT '风险等级',
    credit_score     INT COMMENT '信用评分',
    merchant_level   VARCHAR(8)   COMMENT '商户等级',
    cust_credit_level VARCHAR(8) COMMENT '客户信用等级',
    credit_level     VARCHAR(8) COMMENT '综合信用等级',

    -- 品牌与连锁
    brand_name       VARCHAR(128) COMMENT '品牌名称',
    chain_flag       CHAR(1) COMMENT '是否连锁',
    store_count      INT COMMENT '门店数量',
    biz_area         DECIMAL(8,2) COMMENT '经营面积(m2)',

    -- 来源与数据质量
    cust_source      VARCHAR(32) COMMENT '客户来源渠道',
    data_source      VARCHAR(32) COMMENT '主数据来源',
    data_version     VARCHAR(16) COMMENT '数据版本',
    data_quality     CHAR(1) COMMENT '数据质量等级',

    -- 时间戳
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    etl_date         DATE COMMENT 'ETL日期'
) COMMENT '全量工商企业信息表';


-- ============================================================
-- 数据加工SQL: 基于6张来源表构建全量工商企业信息宽表
-- 特性说明:
--   1. 多表JOIN(LEFT JOIN)：保证企业信息不丢失
--   2. WHERE条件筛选：限定有效数据范围
--   3. 字段映射与转换：类型码转名称、金额单位统一等
--   4. 数据融合：多源同义字段归一化
--   5. 派生字段计算：经营年限、总员工数、综合状态等
-- ============================================================

INSERT INTO t_full_enterprise_info (
    record_id, cust_no, ent_id, merchant_id,
    unified_code, credit_code_status,
    ent_name, ent_short_name, merchant_name, cust_name,
    ent_type_code, ent_type_name, reg_type_code, reg_type_name,
    industry_code, industry_name, biz_category, biz_category_name,
    reg_capital, paid_capital, currency_code, ent_scale,
    establish_date, biz_term_from, biz_term_to, biz_age_years,
    business_status, merchant_status, cust_status, overall_status,
    reg_address, business_address, actual_address,
    province_code, province_name, city_code, city_name,
    district_code, district_name, longitude, latitude,
    legal_name, legal_cert_no, legal_phone, legal_email,
    legal_nationality, legal_gender,
    contact_name, contact_phone, contact_email, contact_address,
    business_scope, reg_org, approval_date,
    tax_no, tax_type, hi_tech_flag, ie_right_flag, listed_flag, listed_code, website,
    ent_staff_count, merchant_staff, total_staff,
    annual_revenue, avg_daily_turnover, monthly_turnover,
    total_assets, annual_income, cust_total_assets,
    settle_account, settle_bank, settle_bank_code, settle_cycle, settle_rate, deposit_amount,
    risk_level, credit_score, merchant_level, cust_credit_level, credit_level,
    brand_name, chain_flag, store_count, biz_area,
    cust_source, data_source, data_version, data_quality,
    create_time, update_time, etl_date
)
SELECT
    -- 主键(使用UUID或复合ID)
    CONCAT(e.ent_id, '_', COALESCE(m.merchant_id, '0')) AS record_id,
    ci.cust_no,
    e.ent_id,
    m.merchant_id,

    -- 统一社会信用代码
    e.unified_code,
    CASE WHEN e.unified_code IS NOT NULL THEN 'VALID' ELSE 'MISSING' END AS credit_code_status,

    -- 名称信息(多源融合，优先企业表)
    e.ent_name,
    COALESCE(e.ent_short_name, SUBSTRING(e.ent_name, 1, 20)) AS ent_short_name,
    m.merchant_name,
    COALESCE(ci.cust_name, e.ent_name) AS cust_name,

    -- 企业类型与分类(码值映射)
    e.ent_type_code,
    COALESCE(uc_type.code_value, e.ent_type_name) AS ent_type_name,
    e.reg_type_code,
    COALESCE(uc_reg.code_value, e.reg_type_name) AS reg_type_name,
    e.industry_code,
    COALESCE(ec_ind.code_item_name, e.industry_name) AS industry_name,
    m.biz_category,
    COALESCE(ec_biz.code_item_name, m.biz_category_name) AS biz_category_name,

    -- 注册资本
    e.reg_capital,
    e.paid_capital,
    e.currency_code,
    COALESCE(uc_scale.code_value, e.ent_scale) AS ent_scale,

    -- 经营时间
    e.establish_date,
    e.biz_term_from,
    e.biz_term_to,
    NULL AS biz_age_years,  -- 由数据生成工具根据规则填充经营年限

    -- 状态融合
    e.business_status,
    m.merchant_status,
    ci.cust_status,
    CASE
        WHEN e.business_status = '2' AND COALESCE(m.merchant_status, '1') = '1' THEN 'ACTIVE'
        WHEN e.business_status IN ('3', '4') THEN 'INACTIVE'
        WHEN COALESCE(m.merchant_status, '1') IN ('3', '4') THEN 'SUSPENDED'
        ELSE 'UNKNOWN'
    END AS overall_status,

    -- 地址
    e.reg_address,
    e.business_address,
    m.actual_address,
    COALESCE(gk_prov.key_value, e.province_code) AS province_code,
    COALESCE(gk_prov.key_name, e.province_name) AS province_name,
    COALESCE(gk_city.key_value, e.city_code) AS city_code,
    COALESCE(gk_city.key_name, e.city_name) AS city_name,
    e.district_code,
    e.district_name,
    e.longitude,
    e.latitude,

    -- 法人信息(优先企业表，证件表补充)
    COALESCE(e.legal_name, lp.cert_name) AS legal_name,
    COALESCE(e.legal_cert_no, lp.cert_no) AS legal_cert_no,
    e.legal_phone,
    e.legal_email,
    lp.nationality AS legal_nationality,
    lp.gender AS legal_gender,

    -- 联系人(商户联系人优先)
    COALESCE(m.contact_name, e.contact_name) AS contact_name,
    COALESCE(m.contact_phone, e.contact_phone) AS contact_phone,
    COALESCE(m.contact_email, e.contact_email) AS contact_email,
    m.contact_address,

    -- 经营范围与资质
    e.business_scope,
    e.reg_org,
    e.approval_date,
    e.tax_no,
    e.tax_type,
    e.hi_tech_flag,
    e.ie_right_flag,
    e.listed_flag,
    e.listed_code,
    e.website,

    -- 人员规模(多源汇总)
    e.staff_count AS ent_staff_count,
    m.staff_count AS merchant_staff,
    COALESCE(e.staff_count, 0) + COALESCE(m.staff_count, 0) AS total_staff,

    -- 财务信息
    e.annual_revenue,
    m.avg_daily_turnover,
    m.monthly_turnover,
    COALESCE(ci.total_assets, 0) AS total_assets,
    ci.annual_income,
    ci.total_assets AS cust_total_assets,

    -- 商户结算
    m.settle_account,
    m.settle_bank,
    m.settle_bank_code,
    m.settle_cycle,
    m.settle_rate,
    m.deposit_amount,

    -- 风险与信用(多源取最严格)
    COALESCE(m.risk_level, ci.risk_level, '2') AS risk_level,
    m.credit_score,
    m.merchant_level,
    ci.credit_level AS cust_credit_level,
    COALESCE(ci.credit_level, m.merchant_level) AS credit_level,

    -- 品牌与连锁
    m.brand_name,
    m.chain_flag,
    m.store_count,
    m.biz_area,

    -- 来源
    ci.source_channel AS cust_source,
    'MULTI_SOURCE' AS data_source,
    '1.0' AS data_version,
    'A' AS data_quality,

    -- 时间戳
    CURRENT_TIMESTAMP AS create_time,
    CURRENT_TIMESTAMP AS update_time,
    CURRENT_DATE AS etl_date

FROM t_enterprise_base e

-- 关联法人商户信息(LEFT JOIN保证无商户的企业也能输出)
LEFT JOIN t_merchant_base m
    ON e.ent_id = m.ent_id

-- 关联客户标识信息(通过商户客户号关联)
LEFT JOIN t_customer_identity ci
    ON m.cust_no = ci.cust_no

-- 关联法人证件信息
LEFT JOIN t_legal_person_cert lp
    ON e.legal_cert_no = lp.cert_no
    AND lp.cert_type = 'ID'
    AND lp.cert_status = '1'

-- 关联工商key(省份)
LEFT JOIN t_gs_key gk_prov
    ON e.province_code = gk_prov.key_value
    AND gk_prov.key_type = 'PROVINCE'

-- 关联工商key(城市)
LEFT JOIN t_gs_key gk_city
    ON e.city_code = gk_city.key_value
    AND gk_city.key_type = 'CITY'

-- 关联上游码值(企业类型)
LEFT JOIN t_upstream_code uc_type
    ON e.ent_type_code = uc_type.code_key
    AND uc_type.code_type = 'ENT_TYPE'
    AND uc_type.valid_flag = '1'

-- 关联上游码值(注册类型)
LEFT JOIN t_upstream_code uc_reg
    ON e.reg_type_code = uc_reg.code_key
    AND uc_reg.code_type = 'REG_TYPE'
    AND uc_reg.valid_flag = '1'

-- 关联上游码值(企业规模)
LEFT JOIN t_upstream_code uc_scale
    ON e.ent_scale = uc_scale.code_key
    AND uc_scale.code_type = 'ENT_SCALE'
    AND uc_scale.valid_flag = '1'

-- 关联外部码值(行业分类)
LEFT JOIN t_external_code ec_ind
    ON e.industry_code = ec_ind.code_item
    AND ec_ind.category_code = 'INDUSTRY'
    AND ec_ind.status = '1'

-- 关联外部码值(经营类目)
LEFT JOIN t_external_code ec_biz
    ON m.biz_category = ec_biz.code_item
    AND ec_biz.category_code = 'BIZ_CATEGORY'
    AND ec_biz.status = '1'

WHERE
    -- 基础筛选条件
    e.business_status IN ('1', '2')
    AND e.unified_code IS NOT NULL
    AND e.unified_code != ''

    -- 成立日期筛选(排除异常日期)
    AND e.establish_date >= '1900-01-01'
    AND e.establish_date <= CURRENT_DATE

    -- 注册资本筛选(排除异常值)
    AND (e.reg_capital IS NULL OR e.reg_capital >= 0)

    -- 经营状态有效
    AND e.biz_term_to > CURRENT_DATE

    -- 商户条件(如有关联商户)
    AND (
        m.merchant_id IS NULL
        OR m.merchant_status IN ('1', '2')
        OR m.merchant_status IS NULL
    )

    -- 客户状态筛选
    AND (
        ci.cust_no IS NULL
        OR ci.cust_status != '4'
    );
