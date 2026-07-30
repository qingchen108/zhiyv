import {
  Badge,
  Button,
  Calendar,
  Card,
  DatePicker,
  Form,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useRequest } from 'ahooks';
import { useState } from 'react';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  pageSchedules,
  createSchedule,
  updateSchedule,
  deleteSchedule,
  suspendSchedule,
  resumeSchedule,
  adjustSlots,
  copyWeek,
} from '@/services/schedule';
import { pageDoctors } from '@/services/doctor';
import { pageDepartments } from '@/services/department';

const PERIOD_OPTIONS = [
  { value: 'MORNING', label: '上午 (08:00-12:00)' },
  { value: 'AFTERNOON', label: '下午 (14:00-17:30)' },
  { value: 'EVENING', label: '晚间 (18:00-21:00)' },
];

const PERIOD_LABEL: Record<string, string> = {
  MORNING: '上午',
  AFTERNOON: '下午',
  EVENING: '晚间',
};

const STATUS_MAP: Record<string, { color: string; text: string }> = {
  PUBLISHED: { color: 'green', text: '已发布' },
  SUSPENDED: { color: 'red', text: '已停诊' },
};

// 排班管理页（仅 ADMIN，04 ticket）
export default function SchedulePage() {
  return (
    <Tabs
      defaultActiveKey="calendar"
      items={[
        { key: 'calendar', label: '日历排班', children: <CalendarTab /> },
        { key: 'overview', label: '号源总览', children: <OverviewTab /> },
      ]}
    />
  );
}

// ==================== 日历排班 Tab ====================
function CalendarTab() {
  const [selectedDate, setSelectedDate] = useState<Dayjs>(dayjs());
  const [form] = Form.useForm();
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [copyOpen, setCopyOpen] = useState(false);
  const [copyForm] = Form.useForm();

  // 医生/科室下拉数据
  const { data: doctorData } = useRequest(() =>
    pageDoctors({ pageNum: 1, pageSize: 100 }).then((r) => r.data),
  );
  const { data: deptData } = useRequest(() =>
    pageDepartments({ pageNum: 1, pageSize: 100 }).then((r) => r.data),
  );

  // 当日排班列表
  const { data, loading, run: load } = useRequest(
    () =>
      pageSchedules({
        date: selectedDate.format('YYYY-MM-DD'),
        pageNum: 1,
        pageSize: 50,
      }).then((r) => r.data),
    { refreshDeps: [selectedDate.format('YYYY-MM-DD')] },
  );

  // 新增/编辑提交
  const { run: onSubmit, loading: submitting } = useRequest(
    async (values: any) => {
      const payload = {
        doctorId: values.doctorId,
        departmentId: values.departmentId,
        scheduleDate: values.scheduleDate.format('YYYY-MM-DD'),
        timePeriod: values.timePeriod,
        totalSlots: values.totalSlots,
      };
      if (editingId) {
        await updateSchedule(editingId, payload);
        message.success('修改成功');
      } else {
        await createSchedule(payload);
        message.success('创建成功');
      }
      setOpen(false);
      load();
    },
    { manual: true, onError: (e: any) => message.error(e?.message || '操作失败') },
  );

  // 周复制提交
  const { run: onCopySubmit, loading: copying } = useRequest(
    async (values: any) => {
      const res = await copyWeek({
        sourceWeekStart: values.sourceWeekStart.format('YYYY-MM-DD'),
        targetWeekStart: values.targetWeekStart.format('YYYY-MM-DD'),
      });
      message.success(`复制完成：新建 ${res.data.created} 条，跳过 ${res.data.skipped} 条`);
      setCopyOpen(false);
      load();
    },
    { manual: true, onError: (e: any) => message.error(e?.message || '复制失败') },
  );

  const openCreate = () => {
    setEditingId(null);
    form.resetFields();
    form.setFieldsValue({ scheduleDate: selectedDate });
    setOpen(true);
  };

  const openEdit = (record: API.Schedule) => {
    setEditingId(record.id);
    form.setFieldsValue({
      doctorId: record.doctorId,
      departmentId: record.departmentId,
      scheduleDate: dayjs(record.scheduleDate),
      timePeriod: record.timePeriod,
      totalSlots: record.totalSlots,
    });
    setOpen(true);
  };

  const onDelete = async (id: number) => {
    try {
      await deleteSchedule(id);
      message.success('删除成功');
      load();
    } catch (e: any) {
      message.error(e?.message || '删除失败');
    }
  };

  const onSuspend = async (id: number) => {
    try {
      await suspendSchedule(id);
      message.success('已停诊');
      load();
    } catch (e: any) {
      message.error(e?.message || '操作失败');
    }
  };

  const onResume = async (id: number) => {
    try {
      await resumeSchedule(id);
      message.success('已恢复');
      load();
    } catch (e: any) {
      message.error(e?.message || '操作失败');
    }
  };

  const columns: ColumnsType<API.Schedule> = [
    {
      title: '医生',
      dataIndex: 'doctorName',
      width: 100,
    },
    {
      title: '科室',
      dataIndex: 'departmentName',
      width: 120,
    },
    {
      title: '班次',
      dataIndex: 'timePeriod',
      width: 80,
      render: (v: string) => PERIOD_LABEL[v] || v,
    },
    {
      title: '时段',
      width: 130,
      render: (_, r) => `${r.startTime?.slice(0, 5)}-${r.endTime?.slice(0, 5)}`,
    },
    {
      title: '号源',
      width: 100,
      render: (_, r) => `${r.remainingSlots} / ${r.totalSlots}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const s = STATUS_MAP[v] || { color: 'default', text: v };
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          {record.status === 'PUBLISHED' ? (
            <Popconfirm title="确认停诊？" onConfirm={() => onSuspend(record.id)}>
              <Button type="link" size="small" danger>
                停诊
              </Button>
            </Popconfirm>
          ) : (
            <Button type="link" size="small" onClick={() => onResume(record.id)}>
              恢复
            </Button>
          )}
          <Popconfirm
            title="确认删除？"
            description="有挂号记录将拒绝删除"
            onConfirm={() => onDelete(record.id)}
            okText="删除"
            okButtonProps={{ danger: true }}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // 日历单元格渲染（显示当天排班数）
  const dateCellRender = (value: Dayjs) => {
    // 简单展示：仅选中日期下方用表格，日历格不额外请求
    return null;
  };

  return (
    <div>
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
        <Space>
          <Button type="primary" onClick={openCreate}>
            新增排班
          </Button>
          <Button onClick={() => { copyForm.resetFields(); setCopyOpen(true); }}>
            批量复制（周）
          </Button>
        </Space>
        <span style={{ color: '#666' }}>
          选中日期：{selectedDate.format('YYYY-MM-DD')}（{['日', '一', '二', '三', '四', '五', '六'][selectedDate.day()]}）
        </span>
      </Space>

      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        {/* 左侧：月历 */}
        <Card style={{ flex: '1 1 400px', minWidth: 320 }}>
          <Calendar
            fullscreen={false}
            value={selectedDate}
            onSelect={(date) => setSelectedDate(date)}
            cellRender={(current, info) => {
              if (info.type !== 'date') return info.originNode;
              return dateCellRender(current as Dayjs);
            }}
          />
        </Card>

        {/* 右侧：当日排班列表 */}
        <Card
          title={`${selectedDate.format('MM-DD')} 排班列表`}
          style={{ flex: '2 1 500px', minWidth: 400 }}
        >
          <Table<API.Schedule>
            rowKey="id"
            columns={columns}
            dataSource={data?.records}
            loading={loading}
            pagination={false}
            size="small"
            scroll={{ x: 700 }}
          />
        </Card>
      </div>

      {/* 新增/编辑弹窗 */}
      <Modal
        title={editingId ? '编辑排班' : '新增排班'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        destroyOnClose
        width={480}
      >
        <Form form={form} layout="vertical" onFinish={onSubmit}>
          <Form.Item label="医生" name="doctorId" rules={[{ required: true, message: '请选择医生' }]}>
            <Select
              placeholder="选择医生"
              showSearch
              optionFilterProp="label"
              options={doctorData?.records?.map((d: API.Doctor) => ({ value: d.id, label: `${d.name}（${d.title || ''}）` }))}
            />
          </Form.Item>
          <Form.Item label="科室" name="departmentId" rules={[{ required: true, message: '请选择科室' }]}>
            <Select
              placeholder="选择科室"
              options={deptData?.records?.map((d: API.Department) => ({ value: d.id, label: d.name }))}
            />
          </Form.Item>
          <Form.Item label="排班日期" name="scheduleDate" rules={[{ required: true, message: '请选择日期' }]}>
            <DatePicker
              style={{ width: '100%' }}
              disabledDate={(current) =>
                current && (current < dayjs().startOf('day') || current > dayjs().add(14, 'day'))
              }
            />
          </Form.Item>
          <Form.Item label="班次" name="timePeriod" rules={[{ required: true, message: '请选择班次' }]}>
            <Select placeholder="选择班次" options={PERIOD_OPTIONS} />
          </Form.Item>
          <Form.Item label="号源总数" name="totalSlots" rules={[{ required: true, message: '请输入号源数' }]}>
            <InputNumber min={1} max={999} style={{ width: '100%' }} placeholder="如 20" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 周复制弹窗 */}
      <Modal
        title="批量排班（周复制）"
        open={copyOpen}
        onCancel={() => setCopyOpen(false)}
        onOk={() => copyForm.submit()}
        confirmLoading={copying}
        destroyOnClose
      >
        <Form form={copyForm} layout="vertical" onFinish={onCopySubmit}>
          <Form.Item
            label="源周起始日（周一）"
            name="sourceWeekStart"
            rules={[{ required: true, message: '请选择源周周一' }]}
          >
            <DatePicker style={{ width: '100%' }} placeholder="选择源周的周一" />
          </Form.Item>
          <Form.Item
            label="目标周起始日（周一）"
            name="targetWeekStart"
            rules={[{ required: true, message: '请选择目标周周一' }]}
          >
            <DatePicker
              style={{ width: '100%' }}
              placeholder="选择目标周的周一"
              disabledDate={(current) => current && current > dayjs().add(14, 'day')}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

// ==================== 号源总览 Tab ====================
function OverviewTab() {
  const [query, setQuery] = useState<{
    pageNum: number;
    pageSize: number;
    departmentId?: number;
    doctorId?: number;
    date?: string;
  }>({ pageNum: 1, pageSize: 10 });

  const [adjustId, setAdjustId] = useState<number | null>(null);
  const [adjustDelta, setAdjustDelta] = useState<number>(0);

  const { data: doctorData } = useRequest(() =>
    pageDoctors({ pageNum: 1, pageSize: 100 }).then((r) => r.data),
  );
  const { data: deptData } = useRequest(() =>
    pageDepartments({ pageNum: 1, pageSize: 100 }).then((r) => r.data),
  );

  const { data, loading, run: load } = useRequest(
    () => pageSchedules(query).then((r) => r.data),
    { refreshDeps: [JSON.stringify(query)] },
  );

  const onAdjust = async () => {
    if (!adjustId || adjustDelta === 0) {
      message.warning('调整量不能为 0');
      return;
    }
    try {
      await adjustSlots(adjustId, adjustDelta);
      message.success('调整成功');
      setAdjustId(null);
      setAdjustDelta(0);
      load();
    } catch (e: any) {
      message.error(e?.message || '调整失败');
    }
  };

  const columns: ColumnsType<API.Schedule> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '日期', dataIndex: 'scheduleDate', width: 110 },
    { title: '医生', dataIndex: 'doctorName', width: 90 },
    { title: '科室', dataIndex: 'departmentName', width: 110 },
    {
      title: '班次',
      dataIndex: 'timePeriod',
      width: 70,
      render: (v: string) => PERIOD_LABEL[v] || v,
    },
    {
      title: '号源（剩余/总数）',
      width: 130,
      render: (_, r) => (
        <span>
          <strong>{r.remainingSlots}</strong> / {r.totalSlots}
          {r.remainingSlots > r.totalSlots && <Tag color="orange" style={{ marginLeft: 4 }}>加号</Tag>}
        </span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: string) => {
        const s = STATUS_MAP[v] || { color: 'default', text: v };
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          onClick={() => {
            setAdjustId(record.id);
            setAdjustDelta(0);
          }}
        >
          调整号源
        </Button>
      ),
    },
  ];

  return (
    <div>
      {/* 筛选栏 */}
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          placeholder="科室"
          allowClear
          style={{ width: 150 }}
          options={deptData?.records?.map((d: API.Department) => ({ value: d.id, label: d.name }))}
          onChange={(v) => setQuery((q) => ({ ...q, departmentId: v, pageNum: 1 }))}
        />
        <Select
          placeholder="医生"
          allowClear
          showSearch
          optionFilterProp="label"
          style={{ width: 150 }}
          options={doctorData?.records?.map((d: API.Doctor) => ({ value: d.id, label: d.name }))}
          onChange={(v) => setQuery((q) => ({ ...q, doctorId: v, pageNum: 1 }))}
        />
        <DatePicker
          placeholder="日期"
          onChange={(d) => setQuery((q) => ({ ...q, date: d?.format('YYYY-MM-DD'), pageNum: 1 }))}
        />
      </Space>

      <Table<API.Schedule>
        rowKey="id"
        columns={columns}
        dataSource={data?.records}
        loading={loading}
        pagination={{
          current: data?.page || 1,
          pageSize: data?.size || 10,
          total: data?.total || 0,
          showSizeChanger: true,
          onChange: (page, pageSize) => setQuery((q) => ({ ...q, pageNum: page, pageSize })),
        }}
        scroll={{ x: 800 }}
      />

      {/* 调整号源弹窗 */}
      <Modal
        title="手动调整号源"
        open={adjustId !== null}
        onCancel={() => setAdjustId(null)}
        onOk={onAdjust}
        okText="确认调整"
      >
        <p>正数为加号，负数为减号。调整后余量不可低于 0。</p>
        <InputNumber
          value={adjustDelta}
          onChange={(v) => setAdjustDelta(v || 0)}
          style={{ width: '100%' }}
          placeholder="如 +5 或 -3"
        />
      </Modal>
    </div>
  );
}
