// App frame and top bar adapted from Watermelon UI's Medesk Dashboard (MIT,
// github.com/WatermelonCorp/watermelon-platform): inset content panel on the sidebar colour, page title with
// a status dot. ChainVax keeps its honesty labels (simulated data, demo thresholds) in the top bar.
import { MoonIcon, SunIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import { Badge } from '../ui'
import { AppSidebar } from './app-sidebar'
import { useTheme } from './theme-provider'

function Topbar({ current }) {
  const { resolvedTheme, setTheme } = useTheme()
  const isDark = resolvedTheme === 'dark'
  const Icon = current?.icon
  return (
    <header className="flex h-16 shrink-0 items-center justify-between gap-4 border-b border-border/60 px-4 md:px-6">
      <div className="flex min-w-0 items-center gap-2">
        <SidebarTrigger className="shrink-0 md:hidden" />
        <div className="flex min-w-0 items-end gap-4">
          <div className="flex h-6 min-w-0 items-center gap-2.5">
            {Icon && <Icon className="size-4 shrink-0" />}
            <h1 className="truncate text-lg leading-6 font-medium">{current?.label}</h1>
          </div>
          <div className="hidden h-6 items-center gap-1.5 lg:flex">
            <span className="relative flex size-1.5">
              <span className="absolute inline-flex size-full animate-ping rounded-full bg-emerald-500 opacity-60" />
              <span className="relative inline-flex size-1.5 rounded-full bg-emerald-500" />
            </span>
            <span className="text-xs text-muted-foreground">
              Live <span className="text-foreground/80">· updates every few seconds</span>
            </span>
          </div>
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-2">
        <div className="hidden items-center gap-1.5 sm:flex">
          <Badge tone="violet">Simulated logger data</Badge>
          <Badge tone="amber">Demo thresholds - not medical guidance</Badge>
        </div>
        <Button
          variant="ghost"
          size="icon"
          aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
          onClick={() => setTheme(isDark ? 'light' : 'dark')}
        >
          {isDark ? <SunIcon className="size-4" /> : <MoonIcon className="size-4" />}
        </Button>
      </div>
    </header>
  )
}

export function AppLayout({ groups, page, children, ...sidebarProps }) {
  const current = groups.flatMap((g) => g.items).find((item) => item.id === page)
  return (
    <SidebarProvider
      defaultOpen
      className="h-svh overflow-hidden"
      style={{ '--sidebar-width': '16rem', '--sidebar-width-icon': '4.5rem' }}
    >
      <AppSidebar groups={groups} page={page} {...sidebarProps} />
      <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-sidebar p-0 md:p-2 md:pl-0">
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-background md:rounded-xl md:shadow-xs">
          <Topbar current={current} />
          <div className="flex-1 overflow-y-auto">{children}</div>
        </div>
      </main>
    </SidebarProvider>
  )
}
