// "Continuous Tabs" from Watermelon UI (MIT, https://ui.watermelon.sh/animated-components/continuous-tabs):
// button-like tabs with a spring-animated sliding pill (Motion layoutId). Adapted to be controlled
// (value/onChange), to take icons, a compact size, and an optional per-tab pill colour.
import { useId } from 'react'
import { LayoutGroup, motion } from 'motion/react'
import { cn } from '@/lib/utils'

export function ContinuousTabs({ tabs, value, onChange, size = 'md', disabled = false, className = '', ariaLabel }) {
  const groupId = useId()
  const small = size === 'sm'
  return (
    <LayoutGroup id={groupId}>
      <nav
        role="tablist"
        aria-label={ariaLabel}
        className={cn(
          'relative inline-flex max-w-full flex-wrap items-center gap-0.5 rounded-full border border-[#E5E5E9] p-1 transition-all duration-300 dark:border-zinc-800',
          'bg-linear-to-b from-[#ffffff] to-[#e9e9f2] dark:from-zinc-900 dark:to-zinc-950',
          'shadow-[inset_0_-2px_4px_rgba(0,0,0,0.08),inset_0_1px_0_rgba(255,255,255,0.9),0_4px_12px_rgba(0,0,0,0.03)]',
          'dark:shadow-[inset_0_-2px_4px_rgba(0,0,0,0.5),inset_0_1px_0_rgba(255,255,255,0.05),0_10px_20px_rgba(0,0,0,0.4)]',
          className,
        )}
      >
        {tabs.map((tab) => {
          const isActive = value === tab.id
          const Icon = tab.icon
          return (
            <button
              key={tab.id}
              type="button"
              role="tab"
              aria-selected={isActive}
              data-tab={tab.id}
              disabled={disabled || tab.disabled}
              onClick={() => onChange?.(tab.id)}
              className={cn(
                'relative cursor-pointer rounded-full outline-none focus-visible:ring-2 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50',
                small ? 'px-2.5 py-1.5' : 'px-4 py-2',
              )}
            >
              {isActive && (
                <motion.div
                  layoutId="active-pill"
                  transition={{ type: 'spring', stiffness: 380, damping: 30, mass: 0.9 }}
                  className={cn('absolute inset-0 rounded-full bg-[#252528] shadow-xs dark:bg-zinc-100', tab.pillClassName)}
                />
              )}
              <motion.span
                layout="position"
                className={cn(
                  'relative z-10 flex items-center gap-1.5 font-semibold whitespace-nowrap transition-colors duration-200',
                  small ? 'text-xs' : 'text-sm',
                  isActive
                    ? cn('text-[#EDEDEC] dark:text-zinc-950', tab.activeTextClassName)
                    : 'text-[#343437] hover:text-[#62625D] dark:text-zinc-500 dark:hover:text-zinc-300',
                )}
              >
                {Icon && <Icon size={small ? 13 : 15} />}
                {tab.label}
              </motion.span>
            </button>
          )
        })}
      </nav>
    </LayoutGroup>
  )
}
