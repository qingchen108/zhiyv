// SmartMed 知识图谱初始化脚本
// 5 类节点: Symptom / Disease / Department / Drug / Allergen
// 6 种关系: INDICATES / BELONGS_TO / TREATED_BY / INTERACTS_WITH / CONTAINS / CONTRAINDICATED_IN
// 数据规模: 约 50 症状 + 30 疾病 + 常见药品与过敏原
// 幂等: 全程使用 MERGE, 可重复执行

// ============ 节点: 科室 Department ============
// 建立约束以保证 MERGE 效率与唯一性
CREATE CONSTRAINT IF NOT EXISTS FOR (d:Department) REQUIRE d.name IS UNIQUE;
CREATE CONSTRAINT IF NOT EXISTS FOR (s:Symptom) REQUIRE s.name IS UNIQUE;
CREATE CONSTRAINT IF NOT EXISTS FOR (d:Disease) REQUIRE d.name IS UNIQUE;
CREATE CONSTRAINT IF NOT EXISTS FOR (d:Drug) REQUIRE d.name IS UNIQUE;
CREATE CONSTRAINT IF NOT EXISTS FOR (a:Allergen) REQUIRE a.name IS UNIQUE;

// ============ 节点: 科室 Department ============
MERGE (d:Department {name: '神经内科'}) SET d.desc = '诊治脑与神经系统疾病';
MERGE (d:Department {name: '呼吸内科'}) SET d.desc = '诊治呼吸道及肺部疾病';
MERGE (d:Department {name: '消化内科'}) SET d.desc = '诊治消化道疾病';
MERGE (d:Department {name: '心血管内科'}) SET d.desc = '诊治心脏与血管疾病';
MERGE (d:Department {name: '内分泌科'}) SET d.desc = '诊治内分泌与代谢疾病';
MERGE (d:Department {name: '骨科'}) SET d.desc = '诊治骨骼、关节、肌肉疾病';
MERGE (d:Department {name: '皮肤科'}) SET d.desc = '诊治皮肤及性传播疾病';
MERGE (d:Department {name: '眼科'}) SET d.desc = '诊治眼部疾病';
MERGE (d:Department {name: '耳鼻喉科'}) SET d.desc = '诊治耳、鼻、咽喉疾病';
MERGE (d:Department {name: '口腔科'}) SET d.desc = '诊治口腔及牙齿疾病';
MERGE (d:Department {name: '泌尿外科'}) SET d.desc = '诊治泌尿与男性生殖系统疾病';
MERGE (d:Department {name: '急诊科'}) SET d.desc = '紧急救治与危重症';
MERGE (d:Department {name: '普通外科'}) SET d.desc = '诊治需手术的综合外科疾病';
MERGE (d:Department {name: '儿科'}) SET d.desc = '诊治儿童疾病';
MERGE (d:Department {name: '血液内科'}) SET d.desc = '诊治血液系统疾病';

// ============ 节点: 症状 Symptom (约 50) ============
MERGE (:Symptom {name: '头痛'});
MERGE (:Symptom {name: '发热'});
MERGE (:Symptom {name: '咳嗽'});
MERGE (:Symptom {name: '咳痰'});
MERGE (:Symptom {name: '咽痛'});
MERGE (:Symptom {name: '鼻塞'});
MERGE (:Symptom {name: '流涕'});
MERGE (:Symptom {name: '胸闷'});
MERGE (:Symptom {name: '胸痛'});
MERGE (:Symptom {name: '心悸'});
MERGE (:Symptom {name: '气促'});
MERGE (:Symptom {name: '呼吸困难'});
MERGE (:Symptom {name: '头晕'});
MERGE (:Symptom {name: '眩晕'});
MERGE (:Symptom {name: '恶心'});
MERGE (:Symptom {name: '呕吐'});
MERGE (:Symptom {name: '腹痛'});
MERGE (:Symptom {name: '腹胀'});
MERGE (:Symptom {name: '腹泻'});
MERGE (:Symptom {name: '便秘'});
MERGE (:Symptom {name: '便血'});
MERGE (:Symptom {name: '反酸'});
MERGE (:Symptom {name: '烧心'});
MERGE (:Symptom {name: '关节痛'});
MERGE (:Symptom {name: '腰背痛'});
MERGE (:Symptom {name: '肌肉酸痛'});
MERGE (:Symptom {name: '皮疹'});
MERGE (:Symptom {name: '瘙痒'});
MERGE (:Symptom {name: '皮肤红肿'});
MERGE (:Symptom {name: '视力模糊'});
MERGE (:Symptom {name: '眼红'});
MERGE (:Symptom {name: '眼痛'});
MERGE (:Symptom {name: '流泪'});
MERGE (:Symptom {name: '耳鸣'});
MERGE (:Symptom {name: '听力下降'});
MERGE (:Symptom {name: '牙痛'});
MERGE (:Symptom {name: '牙龈出血'});
MERGE (:Symptom {name: '口腔溃疡'});
MERGE (:Symptom {name: '尿频'});
MERGE (:Symptom {name: '尿急'});
MERGE (:Symptom {name: '尿痛'});
MERGE (:Symptom {name: '血尿'});
MERGE (:Symptom {name: '多饮'});
MERGE (:Symptom {name: '多尿'});
MERGE (:Symptom {name: '多食'});
MERGE (:Symptom {name: '体重下降'});
MERGE (:Symptom {name: '乏力'});
MERGE (:Symptom {name: '失眠'});
MERGE (:Symptom {name: '焦虑'});
MERGE (:Symptom {name: '大出血'});

// ============ 节点: 疾病 Disease (约 30) ============
MERGE (:Disease {name: '上呼吸道感染'});
MERGE (:Disease {name: '流行性感冒'});
MERGE (:Disease {name: '急性支气管炎'});
MERGE (:Disease {name: '肺炎'});
MERGE (:Disease {name: '偏头痛'});
MERGE (:Disease {name: '紧张性头痛'});
MERGE (:Disease {name: '高血压'});
MERGE (:Disease {name: '冠心病'});
MERGE (:Disease {name: '急性胃肠炎'});
MERGE (:Disease {name: '消化性溃疡'});
MERGE (:Disease {name: '胃食管反流病'});
MERGE (:Disease {name: '急性阑尾炎'});
MERGE (:Disease {name: '糖尿病'});
MERGE (:Disease {name: '甲状腺功能亢进'});
MERGE (:Disease {name: '腰椎间盘突出'});
MERGE (:Disease {name: '骨关节炎'});
MERGE (:Disease {name: '湿疹'});
MERGE (:Disease {name: '荨麻疹'});
MERGE (:Disease {name: '接触性皮炎'});
MERGE (:Disease {name: '急性结膜炎'});
MERGE (:Disease {name: '干眼症'});
MERGE (:Disease {name: '急性中耳炎'});
MERGE (:Disease {name: '过敏性鼻炎'});
MERGE (:Disease {name: '龋齿'});
MERGE (:Disease {name: '牙周炎'});
MERGE (:Disease {name: '泌尿系感染'});
MERGE (:Disease {name: '肾结石'});
MERGE (:Disease {name: '缺铁性贫血'});
MERGE (:Disease {name: '脑卒中'});
MERGE (:Disease {name: '心肌梗死'});
MERGE (:Disease {name: '哮喘'});

// ============ 节点: 药品 Drug ============
MERGE (:Drug {name: '布洛芬'});
MERGE (:Drug {name: '对乙酰氨基酚'});
MERGE (:Drug {name: '阿司匹林'});
MERGE (:Drug {name: '阿莫西林'});
MERGE (:Drug {name: '头孢氨苄'});
MERGE (:Drug {name: '阿奇霉素'});
MERGE (:Drug {name: '奥美拉唑'});
MERGE (:Drug {name: '铝碳酸镁'});
MERGE (:Drug {name: '蒙脱石散'});
MERGE (:Drug {name: '二甲双胍'});
MERGE (:Drug {name: '硝苯地平'});
MERGE (:Drug {name: '氨氯地平'});
MERGE (:Drug {name: '阿托伐他汀'});
MERGE (:Drug {name: '氯雷他定'});
MERGE (:Drug {name: '盐酸西替利嗪'});
MERGE (:Drug {name: '复方甘草片'});
MERGE (:Drug {name: '氨溴索'});
MERGE (:Drug {name: '甲巯咪唑'});
MERGE (:Drug {name: '左氧氟沙星'});
MERGE (:Drug {name: '硝酸甘油'});
MERGE (:Drug {name: '奥司他韦'});

// ============ 节点: 过敏原 Allergen ============
MERGE (:Allergen {name: '青霉素'});
MERGE (:Allergen {name: '头孢类'});
MERGE (:Allergen {name: '磺胺类'});
MERGE (:Allergen {name: '阿司匹林'});
MERGE (:Allergen {name: '海鲜'});
MERGE (:Allergen {name: '花粉'});
MERGE (:Allergen {name: '尘螨'});
MERGE (:Allergen {name: '花生'});

// ============ 关系: BELONGS_TO (Disease -> Department) ============
MATCH (s:Disease {name:'上呼吸道感染'}), (d:Department {name:'呼吸内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'流行性感冒'}), (d:Department {name:'呼吸内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'急性支气管炎'}), (d:Department {name:'呼吸内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'肺炎'}), (d:Department {name:'呼吸内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'偏头痛'}), (d:Department {name:'神经内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'紧张性头痛'}), (d:Department {name:'神经内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'脑卒中'}), (d:Department {name:'神经内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'高血压'}), (d:Department {name:'心血管内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'冠心病'}), (d:Department {name:'心血管内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'心肌梗死'}), (d:Department {name:'心血管内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'急性胃肠炎'}), (d:Department {name:'消化内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'消化性溃疡'}), (d:Department {name:'消化内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'胃食管反流病'}), (d:Department {name:'消化内科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'急性阑尾炎'}), (d:Department {name:'普通外科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'糖尿病'}), (d:Department {name:'内分泌科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'甲状腺功能亢进'}), (d:Department {name:'内分泌科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'腰椎间盘突出'}), (d:Department {name:'骨科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'骨关节炎'}), (d:Department {name:'骨科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'湿疹'}), (d:Department {name:'皮肤科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'荨麻疹'}), (d:Department {name:'皮肤科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'接触性皮炎'}), (d:Department {name:'皮肤科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'急性结膜炎'}), (d:Department {name:'眼科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'干眼症'}), (d:Department {name:'眼科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'急性中耳炎'}), (d:Department {name:'耳鼻喉科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'过敏性鼻炎'}), (d:Department {name:'耳鼻喉科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'龋齿'}), (d:Department {name:'口腔科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'牙周炎'}), (d:Department {name:'口腔科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'泌尿系感染'}), (d:Department {name:'泌尿外科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'肾结石'}), (d:Department {name:'泌尿外科'}) MERGE (s)-[:BELONGS_TO]->(d);
MATCH (s:Disease {name:'缺铁性贫血'}), (d:Department {name:'血液内科'}) MERGE (s)-[:BELONGS_TO]->(d);

// ============ 关系: INDICATES (Symptom -> Disease) ============
MATCH (a:Symptom {name:'头痛'}), (b:Disease {name:'上呼吸道感染'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'头痛'}), (b:Disease {name:'流行性感冒'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'头痛'}), (b:Disease {name:'偏头痛'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'头痛'}), (b:Disease {name:'紧张性头痛'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'头痛'}), (b:Disease {name:'脑卒中'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'发热'}), (b:Disease {name:'上呼吸道感染'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'发热'}), (b:Disease {name:'流行性感冒'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'发热'}), (b:Disease {name:'急性支气管炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'发热'}), (b:Disease {name:'肺炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'发热'}), (b:Disease {name:'急性胃肠炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'发热'}), (b:Disease {name:'泌尿系感染'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'咳嗽'}), (b:Disease {name:'上呼吸道感染'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'咳嗽'}), (b:Disease {name:'急性支气管炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'咳嗽'}), (b:Disease {name:'肺炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'咳痰'}), (b:Disease {name:'急性支气管炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'咳痰'}), (b:Disease {name:'肺炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'咽痛'}), (b:Disease {name:'上呼吸道感染'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'鼻塞'}), (b:Disease {name:'过敏性鼻炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'鼻塞'}), (b:Disease {name:'上呼吸道感染'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'流涕'}), (b:Disease {name:'上呼吸道感染'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'流涕'}), (b:Disease {name:'过敏性鼻炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'胸痛'}), (b:Disease {name:'冠心病'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'胸痛'}), (b:Disease {name:'心肌梗死'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'胸闷'}), (b:Disease {name:'冠心病'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'心悸'}), (b:Disease {name:'高血压'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'气促'}), (b:Disease {name:'肺炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'呼吸困难'}), (b:Disease {name:'心肌梗死'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'呼吸困难'}), (b:Disease {name:'哮喘'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'头晕'}), (b:Disease {name:'高血压'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'眩晕'}), (b:Disease {name:'脑卒中'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'恶心'}), (b:Disease {name:'急性胃肠炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'呕吐'}), (b:Disease {name:'急性胃肠炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'腹痛'}), (b:Disease {name:'急性胃肠炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'腹痛'}), (b:Disease {name:'消化性溃疡'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'腹痛'}), (b:Disease {name:'急性阑尾炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'腹泻'}), (b:Disease {name:'急性胃肠炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'反酸'}), (b:Disease {name:'胃食管反流病'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'烧心'}), (b:Disease {name:'胃食管反流病'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'便血'}), (b:Disease {name:'消化性溃疡'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'多饮'}), (b:Disease {name:'糖尿病'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'多尿'}), (b:Disease {name:'糖尿病'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'多食'}), (b:Disease {name:'糖尿病'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'体重下降'}), (b:Disease {name:'糖尿病'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'体重下降'}), (b:Disease {name:'甲状腺功能亢进'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'乏力'}), (b:Disease {name:'缺铁性贫血'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'乏力'}), (b:Disease {name:'糖尿病'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'关节痛'}), (b:Disease {name:'骨关节炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'腰背痛'}), (b:Disease {name:'腰椎间盘突出'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'皮疹'}), (b:Disease {name:'湿疹'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'皮疹'}), (b:Disease {name:'荨麻疹'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'瘙痒'}), (b:Disease {name:'湿疹'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'瘙痒'}), (b:Disease {name:'接触性皮炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'视力模糊'}), (b:Disease {name:'干眼症'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'眼红'}), (b:Disease {name:'急性结膜炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'眼痛'}), (b:Disease {name:'急性结膜炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'耳鸣'}), (b:Disease {name:'急性中耳炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'听力下降'}), (b:Disease {name:'急性中耳炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'牙痛'}), (b:Disease {name:'龋齿'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'牙龈出血'}), (b:Disease {name:'牙周炎'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'尿频'}), (b:Disease {name:'泌尿系感染'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'尿急'}), (b:Disease {name:'泌尿系感染'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'尿痛'}), (b:Disease {name:'泌尿系感染'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'血尿'}), (b:Disease {name:'肾结石'}) MERGE (a)-[:INDICATES]->(b);
MATCH (a:Symptom {name:'大出血'}), (b:Disease {name:'急性阑尾炎'}) MERGE (a)-[:INDICATES]->(b);

// ============ 关系: TREATED_BY (Disease -> Drug) ============
MATCH (a:Disease {name:'上呼吸道感染'}), (b:Drug {name:'对乙酰氨基酚'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'上呼吸道感染'}), (b:Drug {name:'阿莫西林'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'流行性感冒'}), (b:Drug {name:'对乙酰氨基酚'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'流行性感冒'}), (b:Drug {name:'奥司他韦'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'急性支气管炎'}), (b:Drug {name:'氨溴索'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'急性支气管炎'}), (b:Drug {name:'阿莫西林'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'肺炎'}), (b:Drug {name:'左氧氟沙星'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'肺炎'}), (b:Drug {name:'氨溴索'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'偏头痛'}), (b:Drug {name:'布洛芬'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'紧张性头痛'}), (b:Drug {name:'对乙酰氨基酚'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'急性胃肠炎'}), (b:Drug {name:'蒙脱石散'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'消化性溃疡'}), (b:Drug {name:'奥美拉唑'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'胃食管反流病'}), (b:Drug {name:'奥美拉唑'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'胃食管反流病'}), (b:Drug {name:'铝碳酸镁'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'糖尿病'}), (b:Drug {name:'二甲双胍'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'高血压'}), (b:Drug {name:'氨氯地平'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'高血压'}), (b:Drug {name:'硝苯地平'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'冠心病'}), (b:Drug {name:'阿司匹林'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'冠心病'}), (b:Drug {name:'阿托伐他汀'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'心肌梗死'}), (b:Drug {name:'硝酸甘油'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'甲状腺功能亢进'}), (b:Drug {name:'甲巯咪唑'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'湿疹'}), (b:Drug {name:'氯雷他定'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'荨麻疹'}), (b:Drug {name:'氯雷他定'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'过敏性鼻炎'}), (b:Drug {name:'氯雷他定'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'过敏性鼻炎'}), (b:Drug {name:'盐酸西替利嗪'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'泌尿系感染'}), (b:Drug {name:'左氧氟沙星'}) MERGE (a)-[:TREATED_BY]->(b);
MATCH (a:Disease {name:'急性结膜炎'}), (b:Drug {name:'左氧氟沙星'}) MERGE (a)-[:TREATED_BY]->(b);

// ============ 关系: INTERACTS_WITH (Drug -> Drug, 药物相互作用) ============
MATCH (a:Drug {name:'布洛芬'}), (b:Drug {name:'阿司匹林'}) MERGE (a)-[:INTERACTS_WITH]->(b);
MATCH (a:Drug {name:'阿司匹林'}), (b:Drug {name:'布洛芬'}) MERGE (a)-[:INTERACTS_WITH]->(b);
MATCH (a:Drug {name:'奥美拉唑'}), (b:Drug {name:'氯雷他定'}) MERGE (a)-[:INTERACTS_WITH]->(b);

// ============ 关系: CONTAINS (Drug -> Allergen, 药品含过敏原成分) ============
MATCH (a:Drug {name:'阿莫西林'}), (b:Allergen {name:'青霉素'}) MERGE (a)-[:CONTAINS]->(b);
MATCH (a:Drug {name:'头孢氨苄'}), (b:Allergen {name:'头孢类'}) MERGE (a)-[:CONTAINS]->(b);
MATCH (a:Drug {name:'阿司匹林'}), (b:Allergen {name:'阿司匹林'}) MERGE (a)-[:CONTAINS]->(b);
MATCH (a:Drug {name:'复方甘草片'}), (b:Allergen {name:'磺胺类'}) MERGE (a)-[:CONTAINS]->(b);

// ============ 关系: CONTRAINDICATED_IN (Drug -> Allergen, 直接禁忌) ============
MATCH (a:Drug {name:'阿莫西林'}), (b:Allergen {name:'青霉素'}) MERGE (a)-[:CONTRAINDICATED_IN]->(b);
MATCH (a:Drug {name:'头孢氨苄'}), (b:Allergen {name:'头孢类'}) MERGE (a)-[:CONTRAINDICATED_IN]->(b);
MATCH (a:Drug {name:'阿司匹林'}), (b:Allergen {name:'阿司匹林'}) MERGE (a)-[:CONTRAINDICATED_IN]->(b);
