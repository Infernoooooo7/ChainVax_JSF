// "Labeled Progress Indicator" from Watermelon UI (MIT,
// https://ui.watermelon.sh/animated-components/labeled-progress-indicator): rotating labels with a 3D blur
// flip plus a progress bar with a moving shimmer. Adapted to a compact inline size, a live `value` (0-100),
// warning/danger tones and an always-visible `end` text, so the key number never rotates away.
import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { cn } from '@/lib/utils'

const TONES = {
  warning: 'bg-amber-500 dark:bg-amber-400',
  danger: 'bg-rose-500 dark:bg-rose-500',
}

export function LabeledProgress({ labels, value, tone = 'warning', end, intervalMs = 2400, className = '' }) {
  const [index, setIndex] = useState(0)

  useEffect(() => {
    if (labels.length < 2) return undefined
    const id = setInterval(() => setIndex((prev) => (prev + 1) % labels.length), intervalMs)
    return () => clearInterval(id)
  }, [labels.length, intervalMs])

  const current = index % labels.length
  return (
    <div className={cn('mt-2', className)}>
      <div className="flex items-center justify-between gap-3 text-[11px] leading-4">
        <div className="relative h-4 min-w-0 flex-1 perspective-[800px] transform-3d">
          <AnimatePresence mode="popLayout" initial={false}>
            <motion.span
              key={current}
              initial={{ opacity: 0, y: 8, scale: 1.4, filter: 'blur(4px)', rotateX: -60 }}
              animate={{ opacity: 1, y: 0, scale: 1, filter: 'blur(0px)', rotateX: 0 }}
              exit={{ opacity: 0, filter: 'blur(4px)', rotateX: 90, scale: 0.9 }}
              transition={{ type: 'spring', stiffness: 600, damping: 100, mass: 10 }}
              className="absolute inset-0 origin-bottom truncate font-medium text-muted-foreground will-change-transform"
            >
              {labels[current]}
            </motion.span>
          </AnimatePresence>
        </div>
        {end && <span className="shrink-0 font-medium text-foreground">{end}</span>}
      </div>
      <div className="mt-1.5 h-2 overflow-hidden rounded-full border border-black/5 bg-muted shadow-inner dark:border-white/5">
        <motion.div
          initial={{ width: '0%' }}
          animate={{ width: `${Math.min(100, Math.max(0, value))}%` }}
          transition={{ duration: 1, ease: 'easeOut' }}
          className={cn('relative h-full overflow-hidden rounded-full', TONES[tone] ?? TONES.warning)}
        >
          <motion.div
            initial={{ x: '-100%' }}
            animate={{ x: '200%' }}
            transition={{ duration: intervalMs / 1000, repeat: Infinity, ease: 'linear' }}
            className="absolute inset-y-0 w-full bg-linear-to-r from-transparent via-white/60 to-transparent"
          />
        </motion.div>
      </div>
    </div>
  )
}
