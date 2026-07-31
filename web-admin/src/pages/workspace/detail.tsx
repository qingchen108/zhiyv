import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Divider,
  Empty,
  Form,
  Input,
  List,
  message,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Timeline,
} from 'antd';
import { useRequest } from 'ahooks';
import { history, useParams } from '@umijs/max';
import { useEffect, useRef, useState } from 'react';
import {
  completeConsultation,
  getConsultation,
  getMedicalRecord,
  listMessages,
  listPrescriptionsByConsultation,
  saveDiagnosis,
  sendMessage,
  startConsultation,
} from '@/services/consultation';
import { createPrescription } from '@/services/prescription';
import { pageDrugs } from '@/services/drug';
import { pageTemplates } from '@/services/prescription';

const PERIOD_LABEL: Record<string, string> = {
  MORNING: '上午',
  AFTERNOON: '下午',
  EVENING: '晚间',
};

const STATUS_MAP: Record<string, { color: string; text: string }> = {
  WAITING: { color: 'orange', text: '待接诊' },
  IN_PROGRESS: { color: 'processing', text: '进行中' },
  COMPLETED: { color: 'green', text: '已完成' },
};

// 问诊详情页（左右分栏：左摘要 + 右对话 + 底部诊断/开方，06 ticket）
export default function ConsultationDetailPage() {
  const params = useParams<{ consultationId: string }>();
  const id = Number(params.consultationId);

  const [msgInput, setMsgInput] = useState('');
  const [diagnosis, setDiagnosis] = useState('');
  const [diagnosisDirty, setDiagnosisDirty] = useState(false);
  const [recordOpen, setRecordOpen] = useState(false);
  const msgListRef = useRef<HTMLDivElement>(null);

  // 问诊详情
  const { data: consult, loading, refresh } = useRequest(() => getConsultation(id).then((r) => r.data), {
    onSuccess: (d) => {
      setDiagnosis(d?.diagnosis || '');
      setDiagnosisDirty(false);
    },
  });

  // 消息列表
  const {
    data: msgs,
    loading: msgsLoading,
    refresh: refreshMsgs,
  } = useRequest(() => listMessages(id).then((r) => r.data || []), {
    refreshDeps: [id],
    pollingInterval: consult?.status === 'IN_PROGRESS' ? 5000 : 0,
  });

  // 处方列表
  const { data: prescriptions, refresh: refreshPrescriptions } = useRequest(
    () => listPrescriptionsByConsultation(id).then((r) => r.data || []),
    { refreshDeps: [id] },
  );

  useEffect(() => {
    if (msgListRef.current) {
      msgListRef.current.scrollTop = msgListRef.current.scrollHeight;
    }
  }, [msgs]);

  // 接诊
  const { run: onStart, loading: starting } = useRequest(() => startConsultation(id).then((r) => r.data), {
    manual: true,
    onSuccess: () => {
      message.success('已接诊');
      refresh();
    },
    onError: (e: any) => message.error(e?.message || '接诊失败'),
  });

  // 完成
  const { run: onComplete, loading: completing } = useRequest(() => completeConsultation(id).then((r) => r.data), {
    manual: true,
    onSuccess: () => {
      message.success('问诊已完成');
      refresh();
    },
    onError: (e: any) => message.error(e?.message || '完成失败'),
  });

  // 保存诊断
  const { run: onSaveDiagnosis, loading: savingDiag } = useRequest(
    () => saveDiagnosis(id, diagnosis).then((r) => r.data),
    {
      manual: true,
      onSuccess: () => {
        message.success('诊断已保存');
        setDiagnosisDirty(false);
        refresh();
      },
      onError: (e: any) => message.error(e?.message || '保存失败'),
    },
  );

  // 发消息
  const { run: onSend, loading: sending } = useRequest(
    async () => {
      if (!msgInput.trim()) return;
      await sendMessage(id, msgInput.trim());
      setMsgInput('');
      refreshMsgs();
    },
    { manual: true, onError: (e: any) => message.error(e?.message || '发送失败') },
  );

  const status = consult?.status;
  const statusInfo = STATUS_MAP[status || ''] || { color: 'default', text: status };

  if (loading) return <Spin />;

  return (
    <div style={{ padding: 0 }}>
      <Row gutter={16}>
        {/* 左侧：患者信息 + 预问诊摘要 + 病历 */}
        <Col span={8}>
          <Card
            title="患者信息"
            size="small"
            extra={
              <Button size="small" onClick={() => setRecordOpen(true)}>
                查看病历
              </Button>
            }
          >
            <p>
              <strong>就诊人：</strong>
              {consult?.visitorName}（{consult?.visitorGender}，{consult?.visitorAge ?? '-'}岁）
            </p>
            <p>
              <strong>挂号号：</strong>
              {consult?.regNo}
            </p>
            <p>
              <strong>班次：</strong>
              {PERIOD_LABEL[consult?.timePeriod || ''] || consult?.timePeriod}
            </p>
            <p>
              <strong>状态：</strong>
              <Badge color={statusInfo.color} text={statusInfo.text} />
            </p>
            <Divider style={{ margin: '8px 0' }} />
            <p style={{ fontWeight: 600 }}>预问诊摘要</p>
            <div
              style={{
                background: '#f6ffed',
                border: '1px solid #b7eb8f',
                borderRadius: 4,
                padding: 8,
                minHeight: 60,
                color: consult?.preDiagnosis ? '#164E63' : '#999',
                fontSize: 13,
              }}
            >
              {consult?.preDiagnosis || '暂无预问诊摘要（AI 预问诊未生成）'}
              {consult?.preDiagnosis && (
                <div style={{ marginTop: 6, fontSize: 11, color: '#999' }}>AI 生成，仅供医生参考</div>
              )}
            </div>
          </Card>

          <Card title="历史处方" size="small" style={{ marginTop: 12 }}>
            {prescriptions && prescriptions.length > 0 ? (
              <List
                size="small"
                dataSource={prescriptions}
                renderItem={(p) => (
                  <List.Item>
                    <Space direction="vertical" size={0} style={{ width: '100%' }}>
                      <Space>
                        <Tag color={p.status === 'ACTIVE' ? 'green' : 'red'}>
                          {p.status === 'ACTIVE' ? '生效' : '已撤销'}
                        </Tag>
                        <span style={{ fontSize: 12, color: '#888' }}>
                          {p.createdAt?.slice(0, 16).replace('T', ' ')}
                        </span>
                      </Space>
                      <span style={{ fontSize: 13 }}>{p.diagnosis || '未填诊断'}</span>
                      <span style={{ fontSize: 12, color: '#666' }}>
                        {p.items.map((i) => i.drugName || `药品${i.drugId}`).join('、')}
                      </span>
                    </Space>
                  </List.Item>
                )}
              />
            ) : (
              <Empty description="暂无历史处方" />
            )}
          </Card>
        </Col>

        {/* 右侧：对话区 + 诊断/开方 */}
        <Col span={16}>
          <Card
            title="图文问诊"
            size="small"
            extra={
              <Space>
                {status === 'WAITING' && (
                  <Button type="primary" loading={starting} onClick={onStart}>
                    接诊
                  </Button>
                )}
                {status === 'IN_PROGRESS' && (
                  <Popconfirm title="确认完成问诊？完成后不可重开" onConfirm={onComplete}>
                    <Button loading={completing}>完成问诊</Button>
                  </Popconfirm>
                )}
                <Button onClick={() => history.push('/workspace')}>返回列表</Button>
              </Space>
            }
          >
            {/* 对话区 */}
            <div
              ref={msgListRef}
              style={{
                height: 280,
                overflowY: 'auto',
                background: '#fafafa',
                padding: 12,
                borderRadius: 4,
                marginBottom: 8,
              }}
            >
              {msgsLoading ? (
                <Spin />
              ) : msgs && msgs.length > 0 ? (
                msgs.map((m) => (
                  <div
                    key={m.id}
                    style={{
                      textAlign: m.senderType === 'DOCTOR' ? 'right' : 'left',
                      margin: '6px 0',
                    }}
                  >
                    <Tag color={m.senderType === 'DOCTOR' ? 'cyan' : 'blue'}>
                      {m.senderType === 'DOCTOR' ? '医生' : '患者'}
                    </Tag>
                    <span
                      style={{
                        display: 'inline-block',
                        background: m.senderType === 'DOCTOR' ? '#e6f7ff' : '#fff',
                        border: '1px solid #d9d9d9',
                        borderRadius: 4,
                        padding: '4px 8px',
                        maxWidth: '70%',
                        textAlign: 'left',
                        fontSize: 13,
                      }}
                    >
                      {m.content}
                    </span>
                  </div>
                ))
              ) : (
                <Empty description="暂无消息" />
              )}
            </div>
            <Space.Compact style={{ width: '100%' }}>
              <Input
                placeholder={status === 'IN_PROGRESS' ? '输入消息（仅进行中可发送）' : '接诊后可发送消息'}
                value={msgInput}
                onChange={(e) => setMsgInput(e.target.value)}
                onPressEnter={onSend}
                disabled={status !== 'IN_PROGRESS'}
              />
              <Button type="primary" loading={sending} onClick={onSend} disabled={status !== 'IN_PROGRESS'}>
                发送
              </Button>
            </Space.Compact>

            <Divider style={{ margin: '12px 0' }} />

            {/* 诊断 + 开方 */}
            <PrescriptionEditor
              consultationId={id}
              status={status || 'WAITING'}
              diagnosis={diagnosis}
              onDiagnosisChange={(v) => {
                setDiagnosis(v);
                setDiagnosisDirty(true);
              }}
              onSaveDiagnosis={onSaveDiagnosis}
              savingDiag={savingDiag}
              diagnosisDirty={diagnosisDirty}
              onPrescriptionCreated={refreshPrescriptions}
            />
          </Card>
        </Col>
      </Row>

      {/* 病历弹窗 */}
      <MedicalRecordModal consultationId={id} open={recordOpen} onClose={() => setRecordOpen(false)} />
    </div>
  );
}

// ==================== 处方编辑器（底部诊断/开方） ====================
function PrescriptionEditor({
  consultationId,
  status,
  diagnosis,
  onDiagnosisChange,
  onSaveDiagnosis,
  savingDiag,
  diagnosisDirty,
  onPrescriptionCreated,
}: {
  consultationId: number;
  status: string;
  diagnosis: string;
  onDiagnosisChange: (v: string) => void;
  onSaveDiagnosis: () => void;
  savingDiag: boolean;
  diagnosisDirty: boolean;
  onPrescriptionCreated: () => void;
}) {
  const canPrescribe = status === 'IN_PROGRESS' || status === 'COMPLETED';

  // 药品下拉
  const { data: drugData } = useRequest(() => pageDrugs({ pageNum: 1, pageSize: 100 }).then((r) => r.data));
  // 模板下拉
  const { data: templateData } = useRequest(() => pageTemplates(1, 100).then((r) => r.data));

  const [items, setItems] = useState<API.PrescriptionItem[]>([
    { drugId: undefined as any, usageMethod: '口服', dosage: '', frequency: '每日2次', remark: '' },
  ]);
  const [prescDiagnosis, setPrescDiagnosis] = useState('');
  const [advice, setAdvice] = useState('');
  const [warnings, setWarnings] = useState<API.ContraindicationWarning[]>([]);

  const drugOptions = (drugData?.records || []).map((d) => ({ label: `${d.name}（${d.specification || ''}）`, value: d.id }));
  const templateOptions = (templateData?.records || []).map((t) => ({ label: t.name, value: t.id }));

  // 使用模板预填
  const onUseTemplate = (templateId: number) => {
    const t = templateData?.records.find((x) => x.id === templateId);
    if (t) {
      setItems(t.items.map((i) => ({ ...i })));
      setAdvice(t.advice || '');
      setPrescDiagnosis(t.applicableDiagnosis || '');
      message.success(`已套用模板「${t.name}」`);
    }
  };

  const updateItem = (idx: number, patch: Partial<API.PrescriptionItem>) => {
    setItems(items.map((it, i) => (i === idx ? { ...it, ...patch } : it)));
  };

  const { run: onSubmit, loading: submitting } = useRequest(
    async (force = false) => {
      const validItems = items.filter((i) => i.drugId);
      if (validItems.length === 0) {
        message.warning('请至少添加一种药品');
        return;
      }
      const res = await createPrescription({
        consultationId,
        diagnosis: prescDiagnosis,
        advice,
        force,
        items: validItems,
      });
      if (res.data.warnings && res.data.warnings.length > 0 && !force) {
        setWarnings(res.data.warnings);
        return; // 不清空，等医生确认 force
      }
      message.success('处方已保存');
      setWarnings([]);
      setItems([{ drugId: undefined as any, usageMethod: '口服', dosage: '', frequency: '每日2次', remark: '' }]);
      setPrescDiagnosis('');
      setAdvice('');
      onPrescriptionCreated();
    },
    { manual: true, onError: (e: any) => message.error(e?.message || '开方失败') },
  );

  return (
    <div>
      {/* 诊断区 */}
      <p style={{ fontWeight: 600, marginBottom: 4 }}>医生诊断</p>
      <Space.Compact style={{ width: '100%', marginBottom: 8 }}>
        <Input.TextArea
          value={diagnosis}
          onChange={(e) => onDiagnosisChange(e.target.value)}
          placeholder="输入诊断结论（问诊级，进行中可保存）"
          rows={2}
          disabled={status !== 'IN_PROGRESS'}
        />
      </Space.Compact>
      <Button
        size="small"
        onClick={onSaveDiagnosis}
        loading={savingDiag}
        disabled={status !== 'IN_PROGRESS' || !diagnosisDirty}
        style={{ marginBottom: 12 }}
      >
        保存诊断
      </Button>

      <Divider style={{ margin: '8px 0' }} />

      {/* 开方区 */}
      <div style={{ marginBottom: 8 }}>
        <Space>
          <span style={{ fontWeight: 600 }}>开具处方</span>
          {canPrescribe ? (
            <Select
              placeholder="使用模板预填"
              style={{ width: 200 }}
              options={templateOptions}
              onChange={onUseTemplate}
              allowClear
            />
          ) : (
            <Tag>接诊后可开方</Tag>
          )}
        </Space>
      </div>

      <Input
        placeholder="处方诊断（可选）"
        value={prescDiagnosis}
        onChange={(e) => setPrescDiagnosis(e.target.value)}
        style={{ marginBottom: 6 }}
        disabled={!canPrescribe}
      />

      <Table<API.PrescriptionItem>
        size="small"
        rowKey={(_, i) => String(i)}
        dataSource={items}
        pagination={false}
        columns={[
          {
            title: '药品',
            dataIndex: 'drugId',
            width: 200,
            render: (_, r, i) => (
              <Select
                showSearch
                placeholder="选择药品"
                options={drugOptions}
                value={r.drugId}
                onChange={(v) => updateItem(i, { drugId: v })}
                disabled={!canPrescribe}
                style={{ width: '100%' }}
              />
            ),
          },
          {
            title: '用法',
            dataIndex: 'usageMethod',
            width: 90,
            render: (v, _, i) => (
              <Select
                value={v}
                onChange={(val) => updateItem(i, { usageMethod: val })}
                disabled={!canPrescribe}
                style={{ width: '100%' }}
                options={[
                  { label: '口服', value: '口服' },
                  { label: '外用', value: '外用' },
                  { label: '注射', value: '注射' },
                ]}
              />
            ),
          },
          {
            title: '用量',
            dataIndex: 'dosage',
            width: 100,
            render: (v, _, i) => (
              <Input
                value={v}
                onChange={(e) => updateItem(i, { dosage: e.target.value })}
                placeholder="如 0.3g"
                disabled={!canPrescribe}
              />
            ),
          },
          {
            title: '频次',
            dataIndex: 'frequency',
            width: 110,
            render: (v, _, i) => (
              <Input
                value={v}
                onChange={(e) => updateItem(i, { frequency: e.target.value })}
                placeholder="如 每日2次"
                disabled={!canPrescribe}
              />
            ),
          },
          {
            title: '备注',
            dataIndex: 'remark',
            render: (v, _, i) => (
              <Input
                value={v}
                onChange={(e) => updateItem(i, { remark: e.target.value })}
                disabled={!canPrescribe}
              />
            ),
          },
          {
            title: '',
            width: 60,
            render: (_, __, i) => (
              <Button
                size="small"
                danger
                disabled={!canPrescribe || items.length === 1}
                onClick={() => setItems(items.filter((_, idx) => idx !== i))}
              >
                删除
              </Button>
            ),
          },
        ]}
      />
      <Button
        size="small"
        type="dashed"
        onClick={() =>
          setItems([...items, { drugId: undefined as any, usageMethod: '口服', dosage: '', frequency: '每日2次', remark: '' }])
        }
        disabled={!canPrescribe}
        style={{ margin: '8px 0' }}
      >
        + 添加药品
      </Button>

      <Input.TextArea
        placeholder="医嘱（可选）"
        value={advice}
        onChange={(e) => setAdvice(e.target.value)}
        rows={2}
        style={{ marginBottom: 8 }}
        disabled={!canPrescribe}
      />

      {/* 禁忌警告区 */}
      {warnings.length > 0 && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 8 }}
          message="检测到禁忌风险"
          description={
            <ul style={{ margin: 0, paddingLeft: 16 }}>
              {warnings.map((w, i) => (
                <li key={i}>
                  <Tag color={w.type === 'ALLERGY' ? 'red' : 'orange'}>
                    {w.type === 'ALLERGY' ? '过敏冲突' : '药物相互作用'}
                  </Tag>
                  {w.description}
                </li>
              ))}
            </ul>
          }
        />
      )}

      <Space>
        {warnings.length > 0 ? (
          <Popconfirm title="确认忽略警告强制开方？" onConfirm={() => onSubmit(true)}>
            <Button type="primary" danger loading={submitting} disabled={!canPrescribe}>
              确认风险并强制开方
            </Button>
          </Popconfirm>
        ) : (
          <Button type="primary" loading={submitting} disabled={!canPrescribe} onClick={() => onSubmit(false)}>
            保存处方
          </Button>
        )}
        {warnings.length > 0 && (
          <Button onClick={() => setWarnings([])}>取消</Button>
        )}
      </Space>
    </div>
  );
}

// ==================== 病历查看弹窗 ====================
function MedicalRecordModal({
  consultationId,
  open,
  onClose,
}: {
  consultationId: number;
  open: boolean;
  onClose: () => void;
}) {
  const { data, loading } = useRequest(() => getMedicalRecord(consultationId).then((r) => r.data), {
    ready: open,
    refreshDeps: [consultationId, open],
  });

  return (
    <Modal title="患者病历" open={open} onCancel={onClose} footer={null} width={780}>
      <Spin spinning={loading}>
        {data && (
          <div>
            <Card size="small" title="基本信息" style={{ marginBottom: 12 }}>
              <p>
                <strong>就诊人：</strong>
                {data.visitorName}（{data.visitorGender}，{data.visitorAge ?? '-'}岁）
              </p>
              <p>
                <strong>过敏史：</strong>
                {data.allergyHistory ? (
                  <Tag color="red">{data.allergyHistory}</Tag>
                ) : (
                  <span style={{ color: '#999' }}>无</span>
                )}
              </p>
            </Card>

            <Row gutter={12}>
              <Col span={8}>
                <Card size="small" title="历史挂号" style={{ height: 320, overflow: 'auto' }}>
                  <Timeline
                    items={data.registrations.map((r) => ({
                      children: (
                        <div style={{ fontSize: 12 }}>
                          <div>{r.regNo}</div>
                          <div style={{ color: '#888' }}>
                            {r.doctorName} · {r.departmentName}
                          </div>
                          <div style={{ color: '#888' }}>
                            {r.scheduleDate} {PERIOD_LABEL[r.timePeriod] || ''}
                          </div>
                          <Tag style={{ marginTop: 2 }}>{r.status}</Tag>
                        </div>
                      ),
                    }))}
                  />
                </Card>
              </Col>
              <Col span={7}>
                <Card size="small" title="历史问诊" style={{ height: 320, overflow: 'auto' }}>
                  <Timeline
                    items={data.consultations.map((c) => ({
                      children: (
                        <div style={{ fontSize: 12 }}>
                          <div style={{ color: '#888' }}>{c.doctorName}</div>
                          <div>{c.diagnosis || '未填诊断'}</div>
                          <Tag style={{ marginTop: 2 }}>{c.status}</Tag>
                        </div>
                      ),
                    }))}
                  />
                </Card>
              </Col>
              <Col span={9}>
                <Card size="small" title="历史处方" style={{ height: 320, overflow: 'auto' }}>
                  <List
                    size="small"
                    dataSource={data.prescriptions}
                    renderItem={(p) => (
                      <List.Item style={{ fontSize: 12 }}>
                        <Space direction="vertical" size={0}>
                          <span>{p.diagnosis || '未填诊断'}</span>
                          <span style={{ color: '#666' }}>
                            {p.items.map((i) => i.drugName || `药品${i.drugId}`).join('、')}
                          </span>
                          <Tag style={{ marginTop: 2 }}>{p.status}</Tag>
                        </Space>
                      </List.Item>
                    )}
                  />
                </Card>
              </Col>
            </Row>
          </div>
        )}
      </Spin>
    </Modal>
  );
}
