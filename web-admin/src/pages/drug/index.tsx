import { Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useRequest } from 'ahooks';
import { useEffect, useState } from 'react';
import { pageDrugs, createDrug, updateDrug, deleteDrug } from '@/services/drug';

const DOSAGE_FORMS = ['片剂', '胶囊', '注射剂', '散剂', '颗粒剂', '口服液', '软膏'];

// 药品管理页（仅 ADMIN）
export default function DrugPage() {
  const [form] = Form.useForm();
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [query, setQuery] = useState({ pageNum: 1, pageSize: 10, name: undefined as string | undefined });

  const { data, loading, run: load } = useRequest(
    () => pageDrugs(query).then((r) => r.data),
    { refreshDeps: [JSON.stringify(query)] },
  );

  const { run: onSubmit, loading: submitting } = useRequest(
    async (values: any) => {
      if (editingId) {
        await updateDrug(editingId, values);
        message.success('修改成功');
      } else {
        await createDrug(values);
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

  const openEdit = (record: API.Drug) => {
    setEditingId(record.id);
    form.setFieldsValue(record);
    setOpen(true);
  };

  const onDelete = async (id: number) => {
    try {
      await deleteDrug(id);
      message.success('删除成功');
      load();
    } catch (e: any) {
      message.error(e?.message || '删除失败');
    }
  };

  const columns: ColumnsType<API.Drug> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '药品名称', dataIndex: 'name' },
    { title: '规格', dataIndex: 'specification', width: 140 },
    { title: '生产厂家', dataIndex: 'manufacturer', ellipsis: true },
    {
      title: '价格',
      dataIndex: 'price',
      width: 100,
      render: (v: number) => (v != null ? `¥${v.toFixed(2)}` : '-'),
    },
    { title: '剂型', dataIndex: 'dosageForm', width: 100 },
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
            title="确认删除该药品？"
            description="若有药店库存/处方引用将拒绝删除"
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

  useEffect(() => {
    form.resetFields();
  }, [open]);

  return (
    <div>
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
        <Input.Search
          placeholder="搜索药品名称"
          allowClear
          onSearch={(v) => setQuery((q) => ({ ...q, name: v || undefined, pageNum: 1 }))}
          style={{ width: 260 }}
        />
        <Button type="primary" onClick={openCreate}>
          新增药品
        </Button>
      </Space>

      <Table<API.Drug>
        rowKey="id"
        columns={columns}
        dataSource={data?.records}
        loading={loading}
        scroll={{ x: 900 }}
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
        title={editingId ? '编辑药品' : '新增药品'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={onSubmit}>
          <Form.Item label="药品名称" name="name" rules={[{ required: true, message: '请输入药品名称' }]}>
            <Input placeholder="如 布洛芬" />
          </Form.Item>
          <Form.Item label="规格" name="specification">
            <Input placeholder="如 0.3g×20粒" />
          </Form.Item>
          <Form.Item label="生产厂家" name="manufacturer">
            <Input placeholder="生产厂家（选填）" />
          </Form.Item>
          <Form.Item label="价格（元）" name="price" rules={[{ required: true, message: '请输入价格' }]}>
            <InputNumber min={0} step={0.01} style={{ width: '100%' }} placeholder="如 18.50" />
          </Form.Item>
          <Form.Item label="剂型" name="dosageForm">
            <Select placeholder="选择剂型" options={DOSAGE_FORMS.map((f) => ({ label: f, value: f }))} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
