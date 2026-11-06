// Rolling-digit numbers as used by Watermelon UI's "Run Widget" (MIT,
// https://ui.watermelon.sh/animated-components/run-widget), via NumberFlow (@number-flow/react, MIT).
// NumberFlow draws digits in a shadow DOM without an accessible label, so a visually hidden copy of the
// formatted value is rendered next to it (screen readers, Ctrl+F and copy/paste see the real number).
import NumberFlow from '@number-flow/react'

export function AnimatedNumber({ value, minDecimals = 0, maxDecimals = 2, suffix, className = '' }) {
  const n = value === null || value === undefined || value === '' ? NaN : Number(value)
  if (Number.isNaN(n)) return <span className={className}>-</span>
  const format = { minimumFractionDigits: minDecimals, maximumFractionDigits: maxDecimals, useGrouping: false }
  const text = new Intl.NumberFormat(undefined, format).format(n) + (suffix ?? '')
  return (
    <>
      <NumberFlow
        aria-hidden="true"
        value={n}
        suffix={suffix}
        format={format}
        className={className}
        style={{ fontVariantNumeric: 'tabular-nums' }}
      />
      <span className="sr-only">{text}</span>
    </>
  )
}
