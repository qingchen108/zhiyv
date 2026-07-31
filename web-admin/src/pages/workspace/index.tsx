import { Badge, Button, Card, Space, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useRequest } from 'ahooks';
import { history } from '@umijs/max';
import { useState } from 'react';
import { todayWaiting } from '@/services/consultation';

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

// 医生工作台 - 待接诊列表页（06 ticket）
export default function WorkspacePage() {
  const [query, setQuery] = useState({ pageNum: 1, pageSize: 10 });

  const { data, loading } = useRequest(() => todayWaiting(query.pageNum, query.pageSize).then((r) => r.data), {
    refreshDeps: [JSON.stringify(query)],
  });

  const columns: ColumnsType<API.Consultation> = [
    {
      title: '挂号单号',
      dataIndex: 'regNo',
      width: 160,
    },
    {
      title: '班次',
      dataIndex: 'timePeriod',
      width: 80,
      render: (v: string) => PERIOD_LABEL[v] || v,
    },
    {
      title: '就诊人',
      width: 180,
      render: (_, r) => (
        <Space>
          <span>{r.visitorName}</span>
          {r.visitorGender && <Tag>{r.visitorGender}</Tag>}
          {r.visitorAge != null && <span style={{ color: '#888' }}>{r.visitorAge}岁</span>}
        </Space>
      ),
    },
    {
      title: '预问诊摘要',
      dataIndex: 'preDiagnosisBrief',
      ellipsis: true,
      render: (v: string) => v || <span style={{ color: '#999' }}>暂无预问诊摘要</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => {
        const s = STATUS_MAP[v] || { color: 'default', text: v };
        return <Badge color={s.color} text={s.text} />;
      },
    },
    {
      title: '操作',
      width: 100,
      render: (_, r) => (
        <Button type="link" onClick={() => history.push(`/workspace/${r.id}`)}>
          接诊
        </Button>
      ),
    },
  ];

  return (
    <Card title="今日待接诊" extra={<Button onClick={() => setQuery({ ...query })}>刷新</Button>}>
      <Table<API.Consultation>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data?.records}
        pagination={{
          current: query.pageNum,
          pageSize: query.pageSize,
          total: data?.total || 0,
          onChange: (pageNum, pageSize) => setQuery({ pageNum, pageSize }),
          showSizeChanger: true,
        }}
      />
    </Card>
  );
}
