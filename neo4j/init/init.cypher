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
MERGE (d:Department {name: '妇科'}) SET d.desc = '诊治女性生殖系统疾病';
MERGE (d:Department {name: '泌尿外科'}) SET d.desc = '诊治泌尿与男性生殖系统疾病';
MERGE (d:Department {name: '急诊科'}) SET d.desc = '紧急救治与危重症';
MERGE (d:Department {name: '普通外科'}) SET d.desc = '诊治需手术的综合外科疾病';
MERGE (d:Department {name: '儿科'}) SET d.desc = '诊治儿童疾病';

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
MERGE (s:Disease {name:'上呼吸道感染'})-[:BELONGS_TO]->(d:Department {name:'呼吸内科'});
MERGE (s:Disease {name:'流行性感冒'})-[:BELONGS_TO]->(d:Department {name:'呼吸内科'});
MERGE (s:Disease {name:'急性支气管炎'})-[:BELONGS_TO]->(d:Department {name:'呼吸内科'});
MERGE (s:Disease {name:'肺炎'})-[:BELONGS_TO]->(d:Department {name:'呼吸内科'});
MERGE (s:Disease {name:'偏头痛'})-[:BELONGS_TO]->(d:Department {name:'神经内科'});
MERGE (s:Disease {name:'紧张性头痛'})-[:BELONGS_TO]->(d:Department {name:'神经内科'});
MERGE (s:Disease {name:'脑卒中'})-[:BELONGS_TO]->(d:Department {name:'神经内科'});
MERGE (s:Disease {name:'高血压'})-[:BELONGS_TO]->(d:Department {name:'心血管内科'});
MERGE (s:Disease {name:'冠心病'})-[:BELONGS_TO]->(d:Department {name:'心血管内科'});
MERGE (s:Disease {name:'心肌梗死'})-[:BELONGS_TO]->(d:Department {name:'心血管内科'});
MERGE (s:Disease {name:'急性胃肠炎'})-[:BELONGS_TO]->(d:Department {name:'消化内科'});
MERGE (s:Disease {name:'消化性溃疡'})-[:BELONGS_TO]->(d:Department {name:'消化内科'});
MERGE (s:Disease {name:'胃食管反流病'})-[:BELONGS_TO]->(d:Department {name:'消化内科'});
MERGE (s:Disease {name:'急性阑尾炎'})-[:BELONGS_TO]->(d:Department {name:'普通外科'});
MERGE (s:Disease {name:'糖尿病'})-[:BELONGS_TO]->(d:Department {name:'内分泌科'});
MERGE (s:Disease {name:'甲状腺功能亢进'})-[:BELONGS_TO]->(d:Department {name:'内分泌科'});
MERGE (s:Disease {name:'腰椎间盘突出'})-[:BELONGS_TO]->(d:Department {name:'骨科'});
MERGE (s:Disease {name:'骨关节炎'})-[:BELONGS_TO]->(d:Department {name:'骨科'});
MERGE (s:Disease {name:'湿疹'})-[:BELONGS_TO]->(d:Department {name:'皮肤科'});
MERGE (s:Disease {name:'荨麻疹'})-[:BELONGS_TO]->(d:Department {name:'皮肤科'});
MERGE (s:Disease {name:'接触性皮炎'})-[:BELONGS_TO]->(d:Department {name:'皮肤科'});
MERGE (s:Disease {name:'急性结膜炎'})-[:BELONGS_TO]->(d:Department {name:'眼科'});
MERGE (s:Disease {name:'干眼症'})-[:BELONGS_TO]->(d:Department {name:'眼科'});
MERGE (s:Disease {name:'急性中耳炎'})-[:BELONGS_TO]->(d:Department {name:'耳鼻喉科'});
MERGE (s:Disease {name:'过敏性鼻炎'})-[:BELONGS_TO]->(d:Department {name:'耳鼻喉科'});
MERGE (s:Disease {name:'龋齿'})-[:BELONGS_TO]->(d:Department {name:'口腔科'});
MERGE (s:Disease {name:'牙周炎'})-[:BELONGS_TO]->(d:Department {name:'口腔科'});
MERGE (s:Disease {name:'泌尿系感染'})-[:BELONGS_TO]->(d:Department {name:'泌尿外科'});
MERGE (s:Disease {name:'肾结石'})-[:BELONGS_TO]->(d:Department {name:'泌尿外科'});
MERGE (s:Disease {name:'缺铁性贫血'})-[:BELONGS_TO]->(d:Department {name:'血液内科'});

// ============ 关系: INDICATES (Symptom -> Disease) ============
MERGE (:Symptom {name:'头痛'})-[:INDICATES]->(:Disease {name:'上呼吸道感染'});
MERGE (:Symptom {name:'头痛'})-[:INDICATES]->(:Disease {name:'流行性感冒'});
MERGE (:Symptom {name:'头痛'})-[:INDICATES]->(:Disease {name:'偏头痛'});
MERGE (:Symptom {name:'头痛'})-[:INDICATES]->(:Disease {name:'紧张性头痛'});
MERGE (:Symptom {name:'头痛'})-[:INDICATES]->(:Disease {name:'脑卒中'});
MERGE (:Symptom {name:'发热'})-[:INDICATES]->(:Disease {name:'上呼吸道感染'});
MERGE (:Symptom {name:'发热'})-[:INDICATES]->(:Disease {name:'流行性感冒'});
MERGE (:Symptom {name:'发热'})-[:INDICATES]->(:Disease {name:'急性支气管炎'});
MERGE (:Symptom {name:'发热'})-[:INDICATES]->(:Disease {name:'肺炎'});
MERGE (:Symptom {name:'发热'})-[:INDICATES]->(:Disease {name:'急性胃肠炎'});
MERGE (:Symptom {name:'发热'})-[:INDICATES]->(:Disease {name:'泌尿系感染'});
MERGE (:Symptom {name:'咳嗽'})-[:INDICATES]->(:Disease {name:'上呼吸道感染'});
MERGE (:Symptom {name:'咳嗽'})-[:INDICATES]->(:Disease {name:'急性支气管炎'});
MERGE (:Symptom {name:'咳嗽'})-[:INDICATES]->(:Disease {name:'肺炎'});
MERGE (:Symptom {name:'咳痰'})-[:INDICATES]->(:Disease {name:'急性支气管炎'});
MERGE (:Symptom {name:'咳痰'})-[:INDICATES]->(:Disease {name:'肺炎'});
MERGE (:Symptom {name:'咽痛'})-[:INDICATES]->(:Disease {name:'上呼吸道感染'});
MERGE (:Symptom {name:'鼻塞'})-[:INDICATES]->(:Disease {name:'过敏性鼻炎'});
MERGE (:Symptom {name:'鼻塞'})-[:INDICATES]->(:Disease {name:'上呼吸道感染'});
MERGE (:Symptom {name:'流涕'})-[:INDICATES]->(:Disease {name:'上呼吸道感染'});
MERGE (:Symptom {name:'流涕'})-[:INDICATES]->(:Disease {name:'过敏性鼻炎'});
MERGE (:Symptom {name:'胸痛'})-[:INDICATES]->(:Disease {name:'冠心病'});
MERGE (:Symptom {name:'胸痛'})-[:INDICATES]->(:Disease {name:'心肌梗死'});
MERGE (:Symptom {name:'胸闷'})-[:INDICATES]->(:Disease {name:'冠心病'});
MERGE (:Symptom {name:'心悸'})-[:INDICATES]->(:Disease {name:'高血压'});
MERGE (:Symptom {name:'气促'})-[:INDICATES]->(:Disease {name:'肺炎'});
MERGE (:Symptom {name:'呼吸困难'})-[:INDICATES]->(:Disease {name:'心肌梗死'});
MERGE (:Symptom {name:'呼吸困难'})-[:INDICATES]->(:Disease {name:'哮喘'});
MERGE (:Symptom {name:'头晕'})-[:INDICATES]->(:Disease {name:'高血压'});
MERGE (:Symptom {name:'眩晕'})-[:INDICATES]->(:Disease {name:'脑卒中'});
MERGE (:Symptom {name:'恶心'})-[:INDICATES]->(:Disease {name:'急性胃肠炎'});
MERGE (:Symptom {name:'呕吐'})-[:INDICATES]->(:Disease {name:'急性胃肠炎'});
MERGE (:Symptom {name:'腹痛'})-[:INDICATES]->(:Disease {name:'急性胃肠炎'});
MERGE (:Symptom {name:'腹痛'})-[:INDICATES]->(:Disease {name:'消化性溃疡'});
MERGE (:Symptom {name:'腹痛'})-[:INDICATES]->(:Disease {name:'急性阑尾炎'});
MERGE (:Symptom {name:'腹泻'})-[:INDICATES]->(:Disease {name:'急性胃肠炎'});
MERGE (:Symptom {name:'反酸'})-[:INDICATES]->(:Disease {name:'胃食管反流病'});
MERGE (:Symptom {name:'烧心'})-[:INDICATES]->(:Disease {name:'胃食管反流病'});
MERGE (:Symptom {name:'便血'})-[:INDICATES]->(:Disease {name:'消化性溃疡'});
MERGE (:Symptom {name:'多饮'})-[:INDICATES]->(:Disease {name:'糖尿病'});
MERGE (:Symptom {name:'多尿'})-[:INDICATES]->(:Disease {name:'糖尿病'});
MERGE (:Symptom {name:'多食'})-[:INDICATES]->(:Disease {name:'糖尿病'});
MERGE (:Symptom {name:'体重下降'})-[:INDICATES]->(:Disease {name:'糖尿病'});
MERGE (:Symptom {name:'体重下降'})-[:INDICATES]->(:Disease {name:'甲状腺功能亢进'});
MERGE (:Symptom {name:'乏力'})-[:INDICATES]->(:Disease {name:'缺铁性贫血'});
MERGE (:Symptom {name:'乏力'})-[:INDICATES]->(:Disease {name:'糖尿病'});
MERGE (:Symptom {name:'关节痛'})-[:INDICATES]->(:Disease {name:'骨关节炎'});
MERGE (:Symptom {name:'腰背痛'})-[:INDICATES]->(:Disease {name:'腰椎间盘突出'});
MERGE (:Symptom {name:'皮疹'})-[:INDICATES]->(:Disease {name:'湿疹'});
MERGE (:Symptom {name:'皮疹'})-[:INDICATES]->(:Disease {name:'荨麻疹'});
MERGE (:Symptom {name:'瘙痒'})-[:INDICATES]->(:Disease {name:'湿疹'});
MERGE (:Symptom {name:'瘙痒'})-[:INDICATES]->(:Disease {name:'接触性皮炎'});
MERGE (:Symptom {name:'视力模糊'})-[:INDICATES]->(:Disease {name:'干眼症'});
MERGE (:Symptom {name:'眼红'})-[:INDICATES]->(:Disease {name:'急性结膜炎'});
MERGE (:Symptom {name:'眼痛'})-[:INDICATES]->(:Disease {name:'急性结膜炎'});
MERGE (:Symptom {name:'耳鸣'})-[:INDICATES]->(:Disease {name:'急性中耳炎'});
MERGE (:Symptom {name:'听力下降'})-[:INDICATES]->(:Disease {name:'急性中耳炎'});
MERGE (:Symptom {name:'牙痛'})-[:INDICATES]->(:Disease {name:'龋齿'});
MERGE (:Symptom {name:'牙龈出血'})-[:INDICATES]->(:Disease {name:'牙周炎'});
MERGE (:Symptom {name:'尿频'})-[:INDICATES]->(:Disease {name:'泌尿系感染'});
MERGE (:Symptom {name:'尿急'})-[:INDICATES]->(:Disease {name:'泌尿系感染'});
MERGE (:Symptom {name:'尿痛'})-[:INDICATES]->(:Disease {name:'泌尿系感染'});
MERGE (:Symptom {name:'血尿'})-[:INDICATES]->(:Disease {name:'肾结石'});
MERGE (:Symptom {name:'大出血'})-[:INDICATES]->(:Disease {name:'急性阑尾炎'});

// ============ 关系: TREATED_BY (Disease -> Drug) ============
MERGE (:Disease {name:'上呼吸道感染'})-[:TREATED_BY]->(:Drug {name:'对乙酰氨基酚'});
MERGE (:Disease {name:'上呼吸道感染'})-[:TREATED_BY]->(:Drug {name:'阿莫西林'});
MERGE (:Disease {name:'流行性感冒'})-[:TREATED_BY]->(:Drug {name:'对乙酰氨基酚'});
MERGE (:Disease {name:'流行性感冒'})-[:TREATED_BY]->(:Drug {name:'奥司他韦'});
MERGE (:Disease {name:'急性支气管炎'})-[:TREATED_BY]->(:Drug {name:'氨溴索'});
MERGE (:Disease {name:'急性支气管炎'})-[:TREATED_BY]->(:Drug {name:'阿莫西林'});
MERGE (:Disease {name:'肺炎'})-[:TREATED_BY]->(:Drug {name:'左氧氟沙星'});
MERGE (:Disease {name:'肺炎'})-[:TREATED_BY]->(:Drug {name:'氨溴索'});
MERGE (:Disease {name:'偏头痛'})-[:TREATED_BY]->(:Drug {name:'布洛芬'});
MERGE (:Disease {name:'紧张性头痛'})-[:TREATED_BY]->(:Drug {name:'对乙酰氨基酚'});
MERGE (:Disease {name:'急性胃肠炎'})-[:TREATED_BY]->(:Drug {name:'蒙脱石散'});
MERGE (:Disease {name:'消化性溃疡'})-[:TREATED_BY]->(:Drug {name:'奥美拉唑'});
MERGE (:Disease {name:'胃食管反流病'})-[:TREATED_BY]->(:Drug {name:'奥美拉唑'});
MERGE (:Disease {name:'胃食管反流病'})-[:TREATED_BY]->(:Drug {name:'铝碳酸镁'});
MERGE (:Disease {name:'糖尿病'})-[:TREATED_BY]->(:Drug {name:'二甲双胍'});
MERGE (:Disease {name:'高血压'})-[:TREATED_BY]->(:Drug {name:'氨氯地平'});
MERGE (:Disease {name:'高血压'})-[:TREATED_BY]->(:Drug {name:'硝苯地平'});
MERGE (:Disease {name:'冠心病'})-[:TREATED_BY]->(:Drug {name:'阿司匹林'});
MERGE (:Disease {name:'冠心病'})-[:TREATED_BY]->(:Drug {name:'阿托伐他汀'});
MERGE (:Disease {name:'心肌梗死'})-[:TREATED_BY]->(:Drug {name:'硝酸甘油'});
MERGE (:Disease {name:'甲状腺功能亢进'})-[:TREATED_BY]->(:Drug {name:'甲巯咪唑'});
MERGE (:Disease {name:'湿疹'})-[:TREATED_BY]->(:Drug {name:'氯雷他定'});
MERGE (:Disease {name:'荨麻疹'})-[:TREATED_BY]->(:Drug {name:'氯雷他定'});
MERGE (:Disease {name:'过敏性鼻炎'})-[:TREATED_BY]->(:Drug {name:'氯雷他定'});
MERGE (:Disease {name:'过敏性鼻炎'})-[:TREATED_BY]->(:Drug {name:'盐酸西替利嗪'});
MERGE (:Disease {name:'泌尿系感染'})-[:TREATED_BY]->(:Drug {name:'左氧氟沙星'});
MERGE (:Disease {name:'急性结膜炎'})-[:TREATED_BY]->(:Drug {name:'左氧氟沙星'});

// ============ 关系: INTERACTS_WITH (Drug -> Drug, 药物相互作用) ============
MERGE (:Drug {name:'布洛芬'})-[:INTERACTS_WITH]->(:Drug {name:'阿司匹林'});
MERGE (:Drug {name:'阿司匹林'})-[:INTERACTS_WITH]->(:Drug {name:'布洛芬'});
MERGE (:Drug {name:'奥美拉唑'})-[:INTERACTS_WITH]->(:Drug {name:'氯雷他定'});

// ============ 关系: CONTAINS (Drug -> Allergen, 药品含过敏原成分) ============
MERGE (:Drug {name:'阿莫西林'})-[:CONTAINS]->(:Allergen {name:'青霉素'});
MERGE (:Drug {name:'头孢氨苄'})-[:CONTAINS]->(:Allergen {name:'头孢类'});
MERGE (:Drug {name:'阿司匹林'})-[:CONTAINS]->(:Allergen {name:'阿司匹林'});
MERGE (:Drug {name:'复方甘草片'})-[:CONTAINS]->(:Allergen {name:'磺胺类'});

// ============ 关系: CONTRAINDICATED_IN (Drug -> Allergen, 直接禁忌) ============
MERGE (:Drug {name:'阿莫西林'})-[:CONTRAINDICATED_IN]->(:Allergen {name:'青霉素'});
MERGE (:Drug {name:'头孢氨苄'})-[:CONTRAINDICATED_IN]->(:Allergen {name:'头孢类'});
MERGE (:Drug {name:'阿司匹林'})-[:CONTRAINDICATED_IN]->(:Allergen {name:'阿司匹林'});
