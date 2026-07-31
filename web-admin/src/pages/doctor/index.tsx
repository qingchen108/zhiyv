import { Button, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useRequest } from 'ahooks';
import { useState } from 'react';
import dayjs from 'dayjs';
import { pageDepartments } from '@/services/department';
import {
  pageDoctors,
  createDoctor,
  updateDoctor,
  deleteDoctor,
} from '@/services/doctor';

const TITLES = ['主任医师', '副主任医师', '主治医师', '住院医师'];

// 医生管理页（ADMIN 全部 + DOCTOR 编辑本人；此处为 ADMIN 视图）
export default function DoctorPage() {
  const [form] = Form.useForm();
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [query, setQuery] = useState({
    pageNum: 1,
    pageSize: 10,
    departmentId: undefined as number | undefined,
    name: undefined as string | undefined,
  });

  // 列表
  const { data, loading, run: load } = useRequest(
    () => pageDoctors(query).then((r) => r.data),
    { refreshDeps: [JSON.stringify(query)] },
  );

  // 科室下拉数据
  const { data: deptData } = useRequest(() =>
    pageDepartments({ pageNum: 1, pageSize: 100 }).then((r) => r.data),
  );

  const { run: onSubmit, loading: submitting } = useRequest(
    async (values: any) => {
      const payload = {
        ...values,
        birthDate: values.birthDate ? values.birthDate.format('YYYY-MM-DD') : undefined,
      };
      if (editingId) {
        await updateDoctor(editingId, payload);
        message.success('修改成功');
      } else {
        await createDoctor(payload);
        message.success('新增成功');
      }
      setOpen(false);
      load();
    },
    { manual: true, onError: (e: any) => message.error(e?.message || '操作失败') },
  );

  const openCreate = () => {
    setEditingId(null);
    form.resetFields();
    setOpen(true);
  };

  const openEdit = (record: API.Doctor) => {
    setEditingId(record.id);
    setOpen(true);
    setTimeout(() => {
      form.setFieldsValue({
        ...record,
        birthDate: record.birthDate ? dayjs(record.birthDate) : undefined,
      });
    }, 0);
  };

  const onDelete = async (id: number) => {
    try {
      await deleteDoctor(id);
      message.success('删除成功');
      load();
    } catch (e: any) {
      message.error(e?.message || '删除失败');
    }
  };

  const columns: ColumnsType<API.Doctor> = [
    {
      title: '序号',
      key: 'index',
      width: 60,
      render: (_, __, index) => ((data?.page || 1) - 1) * (data?.size || 10) + index + 1,
    },
    { title: '姓名', dataIndex: 'name', width: 100 },
    { title: '科室', dataIndex: 'departmentName', width: 120, render: (v) => v || '-' },
    { title: '性别', dataIndex: 'gender', width: 70 },
    { title: '年龄', dataIndex: 'age', width: 70 },
    { title: '职称', dataIndex: 'title', width: 110 },
    { title: '擅长', dataIndex: 'specialty', ellipsis: true },
    { title: '好评率', dataIndex: 'goodRate', width: 90, render: (v) => (v != null ? `${v}%` : '-') },
    { title: '手机号', dataIndex: 'phone', width: 130 },
    {
      title: '操作',
      key: 'action',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该医生？"
            description="将同时删除其登录账号；若有排班/挂号引用将拒绝"
            onConfirm={() => onDelete(record.id)}
            okText="删除"
            okButtonProps={{ danger: true }}
            cancelText="取消"
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }} wrap>
        <Space wrap>
          <Select
            allowClear
            placeholder="按科室筛选"
            style={{ width: 180 }}
            value={query.departmentId}
            onChange={(v) => setQuery((q) => ({ ...q, departmentId: v, pageNum: 1 }))}
            options={deptData?.records?.map((d) => ({ label: d.name, value: d.id }))}
          />
          <Input.Search
            placeholder="搜索姓名"
            allowClear
            onSearch={(v) => setQuery((q) => ({ ...q, name: v || undefined, pageNum: 1 }))}
            style={{ width: 200 }}
          />
        </Space>
        <Button type="primary" onClick={openCreate}>
          新增医生
        </Button>
      </Space>

      <Table<API.Doctor>
        rowKey="id"
        columns={columns}
        dataSource={data?.records}
        loading={loading}
        scroll={{ x: 1100 }}
        pagination={{
          current: data?.page || 1,
          pageSize: data?.size || 10,
          total: data?.total || 0,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          onChange: (page, pageSize) => setQuery((q) => ({ ...q, pageNum: page, pageSize })),
        }}
      />

      <Modal
        title={editingId ? '编辑医生' : '新增医生'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        destroyOnClose
        width={620}
      >
        <Form form={form} layout="vertical" onFinish={onSubmit}>
          <Form.Item label="所属科室" name="departmentId" rules={[{ required: true, message: '请选择科室' }]}>
            <Select
              placeholder="选择科室"
              options={deptData?.records?.map((d) => ({ label: d.name, value: d.id }))}
            />
          </Form.Item>
          <Form.Item label="姓名" name="name" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input placeholder="医生姓名" />
          </Form.Item>
          <Space style={{ display: 'flex' }}>
            <Form.Item label="性别" name="gender" style={{ flex: 1 }} rules={[{ required: true, message: '请选择性别' }]}>
              <Select placeholder="选择性别" options={[{ label: '男', value: '男' }, { label: '女', value: '女' }]} />
            </Form.Item>
            <Form.Item label="出生日期" name="birthDate" style={{ flex: 1 }} rules={[{ required: true, message: '请选择出生日期' }]}>
              <DatePicker
                style={{ width: '100%' }}
                placeholder="选择出生日期"
                disabledDate={(current) => current && current > dayjs().endOf('day')}
              />
            </Form.Item>
            <Form.Item label="职称" name="title" style={{ flex: 1 }} rules={[{ required: true, message: '请选择职称' }]}>
              <Select placeholder="选择职称" options={TITLES.map((t) => ({ label: t, value: t }))} />
            </Form.Item>
          </Space>
          <Form.Item label="擅长领域" name="specialty">
            <Input.TextArea placeholder="擅长领域（选填）" rows={2} />
          </Form.Item>
          <Form.Item label="简介" name="intro">
            <Input.TextArea placeholder="医生简介（选填）" rows={2} />
          </Form.Item>
          <Form.Item label="好评率（%）" name="goodRate">
            <InputNumber min={0} max={100} step={0.01} style={{ width: '100%' }} placeholder="如 98.00" />
          </Form.Item>
          <Form.Item label="登录手机号" name="phone" rules={[{ required: true, message: '请输入手机号' }]}>
            <Input placeholder="登录手机号（作登录账号）" />
          </Form.Item>
          {!editingId && (
            <Form.Item label="初始密码" name="password" extra="留空则默认 123456，首登需改密">
              <Input.Password placeholder="初始密码（选填，默认 123456）" />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </div>
  );
}
