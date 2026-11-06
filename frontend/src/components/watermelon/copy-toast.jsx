// "Inline Toast" from Watermelon UI (MIT, https://ui.watermelon.sh/animated-components/inline-toast):
// a copy control whose background fills while a blurred "Copied" state swaps in. Adapted to a small
// monospace chip for SHA-256 hashes (lucide icons instead of react-icons).
import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { CheckCircle2, Copy } from 'lucide-react'
import { cn } from '@/lib/utils'

const swap = { type: 'spring', bounce: 0, duration: 0.4 }

export function CopyToast({ value, display, copyDuration = 1400, className = '' }) {
  const [copied, setCopied] = useState(false)

  async function handleCopy(event) {
    event.stopPropagation()
    try {
      await navigator.clipboard?.writeText(value)
    } catch {
      /* clipboard can be unavailable on plain http; still show the value */
    }
    setCopied(true)
    setTimeout(() => setCopied(false), copyDuration)
  }

  return (
    <motion.button
      type="button"
      layout="position"
      transition={{ type: 'spring', stiffness: 260, damping: 24 }}
      onClick={handleCopy}
      title={`${value}\n(click to copy)`}
      className={cn(
        'relative inline-flex h-6 cursor-pointer items-center overflow-hidden rounded-full border border-border bg-muted px-2 align-middle font-mono text-[11px] text-foreground/80 hover:text-foreground',
        className,
      )}
    >
      <AnimatePresence>
        {copied && (
          <motion.span
            initial={{ x: '-100%' }}
            animate={{ x: '0%' }}
            exit={{ opacity: 0 }}
            transition={{ duration: copyDuration / 1000, ease: 'linear' }}
            className="absolute inset-0 bg-emerald-500/15"
          />
        )}
      </AnimatePresence>
      <AnimatePresence mode="popLayout" initial={false}>
        {!copied ? (
          <motion.span
            key="value"
            initial={{ opacity: 0, filter: 'blur(4px)', scale: 0.95 }}
            animate={{ opacity: 1, filter: 'blur(0px)', scale: 1 }}
            exit={{ opacity: 0, filter: 'blur(4px)', scale: 0.95 }}
            transition={swap}
            className="relative z-10 inline-flex items-center gap-1"
          >
            {display}
            <Copy size={11} className="text-muted-foreground" />
          </motion.span>
        ) : (
          <motion.span
            key="copied"
            initial={{ opacity: 0, filter: 'blur(4px)', scale: 1.1 }}
            animate={{ opacity: 1, filter: 'blur(0px)', scale: 1 }}
            exit={{ opacity: 0, filter: 'blur(4px)', scale: 1.1 }}
            transition={swap}
            className="relative z-10 inline-flex items-center gap-1 font-sans font-medium text-emerald-700 dark:text-emerald-300"
          >
            <CheckCircle2 size={12} />
            Copied
          </motion.span>
        )}
      </AnimatePresence>
    </motion.button>
  )
}
