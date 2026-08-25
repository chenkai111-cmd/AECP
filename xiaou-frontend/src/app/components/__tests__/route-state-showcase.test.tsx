import { render, screen } from '@testing-library/react'

import { RouteStateShowcase } from '../RouteStateShowcase'

describe('RouteStateShowcase', () => {
  it('renders reusable state cards with the provided label and tone classes', () => {
    render(
      <RouteStateShowcase
        ariaLabel="自定义状态展示"
        cards={[
          {
            description: '异步请求仍在进行中。',
            title: '加载中',
            tone: 'loading'
          },
          {
            description: '当前还没有任何记录。',
            title: '暂无数据',
            tone: 'empty'
          },
          {
            description: '服务暂时不可用。',
            title: '示例错误态',
            tone: 'error'
          }
        ]}
      />
    )

    expect(screen.getByRole('region', { name: '自定义状态展示' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '加载中' }).closest('article')).toHaveClass('status-loading')
    expect(screen.getByRole('heading', { name: '暂无数据' }).closest('article')).toHaveClass('status-empty')
    expect(screen.getByRole('heading', { name: '示例错误态' }).closest('article')).toHaveClass('status-error')
  })
})
