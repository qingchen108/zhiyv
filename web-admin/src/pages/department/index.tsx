import { Button, Form, Input, Modal, Popconfirm, Space, Table, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useRequest } from 'ahooks';
import { useEffect, useState } from 'react';
import {
  pageDepartments,
  createDepartment,
  updateDepartment,
  deleteDepartment,
} from '@/services/department';

// 科室管理页（仅 ADMIN）
export default function DepartmentPage() {
  const [form] = Form.useForm();
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [query, setQuery] = useState({ pageNum: 1, pageSize: 10, name: undefined as string | undefined });

  // 列表查询
  const { data, loading, run: load } = useRequest(
    () => pageDepartments(query).then((r) => r.data),
    { refreshDeps: [JSON.stringify(query)] },
  );

  // 新增/编辑提交
  const { run: onSubmit, loading: submitting } = useRequest(
    async (values: { name: string; description?: string }) => {
      if (editingId) {
        await updateDepartment(editingId, values);
        message.success('修改成功');
      } else {
        await createDepartment(values);
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

  const openEdit = (record: API.Department) => {
    setEditingId(record.id);
    form.setFieldsValue(record);
    setOpen(true);
  };

  const onDelete = async (id: number) => {
    try {
      await deleteDepartment(id);
      message.success('删除成功');
      load();
    } catch (e: any) {
      message.error(e?.message || '删除失败');
    }
  };

  const columns: ColumnsType<API.Department> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '科室名称', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该科室？"
            description="若有医生/排班引用将拒绝删除"
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
          placeholder="搜索科室名称"
          allowClear
          onSearch={(v) => setQuery((q) => ({ ...q, name: v || undefined, pageNum: 1 }))}
          style={{ width: 260 }}
        />
        <Button type="primary" onClick={openCreate}>
          新增科室
        </Button>
      </Space>

      <Table<API.Department>
        rowKey="id"
        columns={columns}
        dataSource={data?.records}
        loading={loading}
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
        title={editingId ? '编辑科室' : '新增科室'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={onSubmit}>
          <Form.Item label="科室名称" name="name" rules={[{ required: true, message: '请输入科室名称' }]}>
            <Input placeholder="如 呼吸内科" />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea placeholder="科室描述（选填）" rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
