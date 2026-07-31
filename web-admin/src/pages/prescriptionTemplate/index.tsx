import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useRequest } from 'ahooks';
import { useState } from 'react';
import { pageDrugs } from '@/services/drug';
import { createTemplate, deleteTemplate, pageTemplates, updateTemplate } from '@/services/prescription';

const emptyItem = () => ({
  drugId: undefined as any,
  usageMethod: '口服',
  dosage: '',
  frequency: '每日2次',
  remark: '',
});

// 处方模板管理页（仅 DOCTOR，06 ticket）
export default function PrescriptionTemplatePage() {
  const [form] = Form.useForm();
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [items, setItems] = useState<API.PrescriptionItem[]>([emptyItem()]);
  const [query, setQuery] = useState({ pageNum: 1, pageSize: 10 });

  const { data, loading, run: load } = useRequest(
    () => pageTemplates(query.pageNum, query.pageSize).then((r) => r.data),
    { refreshDeps: [JSON.stringify(query)] },
  );

  // 药品下拉
  const { data: drugData } = useRequest(() => pageDrugs({ pageNum: 1, pageSize: 100 }).then((r) => r.data));
  const drugOptions = (drugData?.records || []).map((d) => ({
    label: `${d.name}（${d.specification || ''}）`,
    value: d.id,
  }));

  const { run: onSubmit, loading: submitting } = useRequest(
    async () => {
      const values = await form.validateFields();
      const validItems = items.filter((i) => i.drugId);
      if (validItems.length === 0) {
        message.warning('请至少添加一种药品');
        return;
      }
      const payload = {
        name: values.name,
        applicableDiagnosis: values.applicableDiagnosis,
        advice: values.advice,
        items: validItems,
      };
      if (editingId) {
        await updateTemplate(editingId, payload);
        message.success('修改成功');
      } else {
        await createTemplate(payload);
        message.success('新建成功');
      }
      setOpen(false);
      load();
    },
    { manual: true, onError: (e: any) => message.error(e?.message || '操作失败') },
  );

  const openCreate = () => {
    setEditingId(null);
    form.resetFields();
    setItems([emptyItem()]);
    setOpen(true);
  };

  const openEdit = (record: API.PrescriptionTemplate) => {
    setEditingId(record.id);
    form.setFieldsValue({
      name: record.name,
      applicableDiagnosis: record.applicableDiagnosis,
      advice: record.advice,
    });
    setItems(record.items.length > 0 ? record.items.map((i) => ({ ...i })) : [emptyItem()]);
    setOpen(true);
  };

  const { run: onDelete } = useRequest(
    async (id: number) => {
      await deleteTemplate(id);
      message.success('删除成功');
      load();
    },
    { manual: true, onError: (e: any) => message.error(e?.message || '删除失败') },
  );

  const columns: ColumnsType<API.PrescriptionTemplate> = [
    { title: '模板名称', dataIndex: 'name', width: 160 },
    { title: '适用诊断', dataIndex: 'applicableDiagnosis', width: 140 },
    {
      title: '药品',
      dataIndex: 'items',
      render: (items: API.PrescriptionItem[]) => items.map((i) => i.drugName || `药品${i.drugId}`).join('、'),
    },
    {
      title: '操作',
      width: 140,
      render: (_, r) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEdit(r)}>
            编辑
          </Button>
          <Popconfirm title="确认删除？" onConfirm={() => onDelete(r.id)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card title="处方模板" extra={<Button type="primary" onClick={openCreate}>新建模板</Button>}>
      <Table<API.PrescriptionTemplate>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data?.records}
        pagination={{
          current: query.pageNum,
          pageSize: query.pageSize,
          total: data?.total || 0,
          onChange: (pageNum, pageSize) => setQuery({ pageNum, pageSize }),
        }}
      />

      <Modal
        title={editingId ? '编辑模板' : '新建模板'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={onSubmit}
        confirmLoading={submitting}
        width={720}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]}>
            <Input placeholder="如：感冒常用药" />
          </Form.Item>
          <Form.Item name="applicableDiagnosis" label="适用诊断">
            <Input placeholder="如：上呼吸道感染" />
          </Form.Item>
          <Form.Item name="advice" label="医嘱">
            <Input.TextArea rows={2} placeholder="如：多饮水，注意休息" />
          </Form.Item>
        </Form>

        <p style={{ fontWeight: 600, marginTop: 8 }}>药品明细</p>
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
                  onChange={(v) => setItems(items.map((it, idx) => (idx === i ? { ...it, drugId: v } : it)))}
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
                  onChange={(val) => setItems(items.map((it, idx) => (idx === i ? { ...it, usageMethod: val } : it)))}
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
                  onChange={(e) => setItems(items.map((it, idx) => (idx === i ? { ...it, dosage: e.target.value } : it)))}
                  placeholder="如 0.3g"
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
                  onChange={(e) => setItems(items.map((it, idx) => (idx === i ? { ...it, frequency: e.target.value } : it)))}
                  placeholder="如 每日2次"
                />
              ),
            },
            {
              title: '备注',
              dataIndex: 'remark',
              render: (v, _, i) => (
                <Input
                  value={v}
                  onChange={(e) => setItems(items.map((it, idx) => (idx === i ? { ...it, remark: e.target.value } : it)))}
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
                  disabled={items.length === 1}
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
          onClick={() => setItems([...items, emptyItem()])}
          style={{ marginTop: 8 }}
        >
          + 添加药品
        </Button>
      </Modal>
    </Card>
  );
}
