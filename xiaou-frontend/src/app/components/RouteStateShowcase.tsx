export type RouteStateTone = 'loading' | 'empty' | 'error'

export type RouteStateCard = {
  description: string
  title: string
  tone: RouteStateTone
}

export const defaultRouteStateCards: RouteStateCard[] = [
  {
    title: '加载中',
    tone: 'loading',
    description: '用于后续接口请求、模型加载和延迟初始化过程。'
  },
  {
    title: '暂无数据',
    tone: 'empty',
    description: '当后续任务尚未返回业务实体时，统一展示空态说明和下一步提示。'
  },
  {
    title: '示例错误态',
    tone: 'error',
    description: '当前只展示可复用样式，真实错误码、重试策略和审计行为由后续任务负责。'
  }
]

export function StatusCard({ description, title, tone }: RouteStateCard) {
  return (
    <article className={`status-card status-${tone}`}>
      <h2>{title}</h2>
      <p>{description}</p>
    </article>
  )
}

type RouteStateShowcaseProps = {
  ariaLabel: string
  cards?: RouteStateCard[]
}

export function RouteStateShowcase({
  ariaLabel,
  cards = defaultRouteStateCards
}: RouteStateShowcaseProps) {
  return (
    <section aria-label={ariaLabel} className="state-grid" role="region">
      {cards.map((card) => (
        <StatusCard
          description={card.description}
          key={`${card.tone}-${card.title}`}
          title={card.title}
          tone={card.tone}
        />
      ))}
    </section>
  )
}
