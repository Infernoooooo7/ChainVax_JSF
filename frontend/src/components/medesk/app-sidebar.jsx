// Sidebar adapted from Watermelon UI's Medesk Dashboard (MIT, github.com/WatermelonCorp/watermelon-platform):
// same structure, menu-button styling, collapsible groups and account menu; ChainVax pages, demo-user
// switching and sign-out instead of Medesk's sample navigation.
import {
  Check,
  ChevronRight,
  ChevronsUpDown,
  ExternalLink,
  LogOutIcon,
  MoonIcon,
  PanelLeftClose,
  Settings2,
  Snowflake,
  SunIcon,
} from 'lucide-react'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  useSidebar,
} from '@/components/ui/sidebar'
import { cn } from '@/lib/utils'
import { roleLabel } from '../ui'
import { useTheme } from './theme-provider'

const menuButtonClassName = cn(
  'h-11 cursor-pointer gap-2.5 rounded-lg bg-transparent py-2.5 pr-2 pl-3 text-[0.9375rem] font-normal text-muted-foreground transition-colors',
  'hover:!bg-transparent hover:text-foreground active:!bg-transparent',
  'aria-[current=page]:!bg-background aria-[current=page]:font-medium aria-[current=page]:text-foreground aria-[current=page]:shadow-xs',
  'data-[state=open]:!bg-transparent data-[state=open]:text-foreground',
  '[&_svg]:size-[1.15rem]! [&_svg]:shrink-0',
  'group-data-[collapsible=icon]:size-11! group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:p-0! group-data-[collapsible=icon]:[&>span]:hidden',
)

const groupLabelClassName = 'h-auto px-0 py-1 text-sm font-normal text-foreground/60 transition-colors'

function initials(name = '') {
  const parts = name.replace(/\(.*?\)/g, '').trim().split(/\s+/).filter(Boolean)
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || '?'
}

function NavItem({ item, active, onSelect }) {
  const { isMobile, setOpenMobile } = useSidebar()
  const Icon = item.icon
  return (
    <SidebarMenuButton asChild tooltip={item.label} className={menuButtonClassName}>
      <button
        type="button"
        aria-current={active ? 'page' : undefined}
        onClick={() => {
          onSelect(item.id)
          if (isMobile) setOpenMobile(false)
        }}
      >
        <Icon />
        <span>{item.label}</span>
      </button>
    </SidebarMenuButton>
  )
}

export function AppSidebar({ groups, page, go, user, demoUsers, onSwitchUser, onSignOut }) {
  const { resolvedTheme, setTheme } = useTheme()
  const { state, toggleSidebar } = useSidebar()
  const isDark = resolvedTheme === 'dark'

  return (
    <Sidebar collapsible="icon" className="border-r-0!">
      <SidebarHeader className="relative h-18 flex-row items-center justify-between gap-3 px-4 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-0">
        <div className="flex min-w-0 items-center gap-2.5 group-data-[collapsible=icon]:hidden">
          <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Snowflake className="size-4.5" />
          </div>
          <div className="min-w-0 leading-tight">
            <div className="truncate text-lg font-medium tracking-tight">ChainVax</div>
            <div className="truncate text-[11px] text-muted-foreground">cold-chain ledger · demo</div>
          </div>
        </div>
        <Button
          variant="ghost"
          onClick={toggleSidebar}
          aria-label={state === 'expanded' ? 'Collapse sidebar' : 'Expand sidebar'}
          className="size-9"
        >
          <PanelLeftClose className="size-4.5 transition-transform group-data-[collapsible=icon]:rotate-180" />
        </Button>
      </SidebarHeader>

      <SidebarContent className="gap-3 px-3 py-2 group-data-[collapsible=icon]:overflow-auto!">
        {groups.map((group) => (
          <Collapsible key={group.label} defaultOpen className="group/collapsible">
            <SidebarGroup className="gap-1 p-0">
              <SidebarGroupLabel asChild className={groupLabelClassName}>
                <CollapsibleTrigger className="flex w-full cursor-pointer items-center justify-between px-1 hover:text-foreground">
                  <span>{group.label}</span>
                  <ChevronRight className="ml-auto size-4 transition-transform group-data-[state=open]/collapsible:rotate-90" />
                </CollapsibleTrigger>
              </SidebarGroupLabel>
              <CollapsibleContent>
                <SidebarGroupContent>
                  <SidebarMenu className="gap-0.5">
                    {group.items.map((item) => (
                      <SidebarMenuItem key={item.id}>
                        {item.href ? (
                          <SidebarMenuButton asChild tooltip={item.label} className={menuButtonClassName}>
                            <a href={item.href} target="_blank" rel="noreferrer">
                              <item.icon />
                              <span>{item.label}</span>
                              <ExternalLink className="ml-auto size-3.5! opacity-50" />
                            </a>
                          </SidebarMenuButton>
                        ) : (
                          <NavItem item={item} active={page === item.id} onSelect={go} />
                        )}
                      </SidebarMenuItem>
                    ))}
                  </SidebarMenu>
                </SidebarGroupContent>
              </CollapsibleContent>
            </SidebarGroup>
          </Collapsible>
        ))}
      </SidebarContent>

      <SidebarFooter className="px-3 pt-2 pb-3">
        <SidebarGroupLabel className={cn(groupLabelClassName, 'px-1 group-data-[collapsible=icon]:hidden')}>
          Signed in
        </SidebarGroupLabel>
        <SidebarMenu>
          <SidebarMenuItem>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <SidebarMenuButton
                  size="lg"
                  aria-label="Account and demo users"
                  className="h-auto cursor-pointer gap-2.5 rounded-lg bg-background px-2 py-2 shadow-xs data-[state=open]:bg-background group-data-[collapsible=icon]:size-11! group-data-[collapsible=icon]:p-1.5!"
                >
                  <Avatar className="size-8 border border-border/50">
                    <AvatarFallback className="text-xs">{initials(user.fullName || user.username)}</AvatarFallback>
                  </Avatar>
                  <div className="grid min-w-0 flex-1 text-left leading-tight group-data-[collapsible=icon]:hidden">
                    <span className="truncate text-sm font-medium">{user.username}</span>
                    <span className="truncate text-xs text-muted-foreground">
                      {roleLabel(user.role)}
                      {user.siteCode ? ` @ ${user.siteCode}` : ''}
                    </span>
                  </div>
                  <ChevronsUpDown className="ml-auto size-4 text-muted-foreground group-data-[collapsible=icon]:hidden" />
                </SidebarMenuButton>
              </DropdownMenuTrigger>
              <DropdownMenuContent side="top" align="start" className="w-72">
                <DropdownMenuLabel className="font-normal">
                  <p className="text-sm font-medium">{user.fullName}</p>
                  <p className="text-xs text-muted-foreground">
                    {roleLabel(user.role)}
                    {user.siteCode ? ` · ${user.siteCode}` : ' · district-wide'}
                  </p>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuLabel className="text-xs font-medium text-muted-foreground">Switch demo user</DropdownMenuLabel>
                <DropdownMenuGroup>
                  {demoUsers.map((d) => (
                    <DropdownMenuItem key={d.username} onClick={() => onSwitchUser(d.username)} data-demo-user={d.username}>
                      <Avatar className="size-6">
                        <AvatarFallback className="text-[10px]">{initials(d.fullName || d.username)}</AvatarFallback>
                      </Avatar>
                      <span className="min-w-0 flex-1 truncate">
                        {d.username} <span className="text-muted-foreground">({roleLabel(d.role)})</span>
                      </span>
                      {d.username === user.username && <Check className="size-4" />}
                    </DropdownMenuItem>
                  ))}
                </DropdownMenuGroup>
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={() => setTheme(isDark ? 'light' : 'dark')}>
                  {isDark ? <SunIcon /> : <MoonIcon />}
                  <span>{isDark ? 'Light mode' : 'Dark mode'}</span>
                </DropdownMenuItem>
                <DropdownMenuItem asChild>
                  <a href="/admin/" target="_blank" rel="noreferrer">
                    <Settings2 />
                    <span>Admin console (separate login)</span>
                  </a>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem variant="destructive" onClick={onSignOut}>
                  <LogOutIcon />
                  <span>Sign out</span>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
