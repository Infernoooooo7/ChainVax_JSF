// "Budget Card" from Watermelon UI (MIT, https://ui.watermelon.sh/animated-components/budget-card):
// corner-bracket frame, large figure, animated spending bar and breakdown. Adapted for vaccine stock:
// the "budget" is the vials that passed through a site and the breakdown says where they are now.
import { motion } from 'motion/react'
import { cn } from '@/lib/utils'
import { AnimatedNumber } from './animated-number'

const smooth = { duration: 1.5, ease: [0.19, 1, 0.22, 1] }
const corner = 'pointer-events-none absolute z-10 size-4 border-foreground/25'

export function StockCard({ title, badge, total, unit = 'vials', caption, segments, selected = false, onClick }) {
  const sum = segments.reduce((acc, s) => acc + s.value, 0)
  return (
    <motion.div
      initial={{ opacity: 0, y: 20, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ duration: 0.8, ease: [0.19, 1, 0.22, 1] }}
      className="relative"
    >
      <span className={`${corner} top-0 left-0 border-t-[1.6px] border-l-[1.6px]`} />
      <span className={`${corner} top-0 right-0 border-t-[1.6px] border-r-[1.6px]`} />
      <span className={`${corner} bottom-0 left-0 border-b-[1.6px] border-l-[1.6px]`} />
      <span className={`${corner} right-0 bottom-0 border-r-[1.6px] border-b-[1.6px]`} />
      <button
        type="button"
        onClick={onClick}
        className={cn(
          'relative block w-full cursor-pointer border-[1.6px] bg-background p-5 text-left transition-colors',
          selected ? 'border-foreground/40' : 'border-black/8 hover:border-foreground/20 dark:border-white/10',
        )}
      >
        <div className="flex items-start justify-between gap-3">
          <p className="text-sm text-muted-foreground">{title}</p>
          {badge}
        </div>
        <div className="mt-1 flex items-baseline gap-2">
          <AnimatedNumber value={total} className="text-[2.5rem] leading-none font-medium tracking-tight" />
          <span className="text-sm text-muted-foreground">{unit}</span>
        </div>
        {caption && <p className="mt-2 text-xs text-muted-foreground">{caption}</p>}
        <div className="mt-5 flex h-2 w-full overflow-hidden bg-muted">
          {segments.map((s) => (
            <motion.div
              key={s.label}
              initial={{ width: 0 }}
              animate={{ width: sum ? `${(s.value / sum) * 100}%` : 0 }}
              transition={smooth}
              className={cn('h-full', s.className)}
            />
          ))}
        </div>
        <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2">
          {segments.map((s) => (
            <div key={s.label} className="min-w-0">
              <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span className={cn('size-2 shrink-0', s.className)} />
                <span className="truncate">{s.label}</span>
              </span>
              <AnimatedNumber value={s.value} className="text-sm font-medium" />
            </div>
          ))}
        </div>
      </button>
    </motion.div>
  )
}
