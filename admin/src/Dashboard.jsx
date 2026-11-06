// Admin dashboard: live counts from GET /api/admin/stats, each card links to its resource.
import { useCallback, useEffect, useState } from 'react';
import { Alert, Box, Card, CardActionArea, CardContent, Chip, LinearProgress, Stack, Typography } from '@mui/material';
import { Button, Link, Title, useDataProvider, useNotify } from 'react-admin';
import RefreshIcon from '@mui/icons-material/Refresh';
import { shortHash } from './fields';

const StatCard = ({ title, value, to, children }) => (
  <Card variant="outlined">
    <CardActionArea component={Link} to={to} sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="overline" color="text.secondary">
          {title}
        </Typography>
        <Typography variant="h4">{value}</Typography>
        {children}
      </CardContent>
    </CardActionArea>
  </Card>
);

export const Dashboard = () => {
  const dataProvider = useDataProvider();
  const notify = useNotify();
  const [stats, setStats] = useState(null);

  const load = useCallback(
    () =>
      dataProvider
        .getStats()
        .then(setStats)
        .catch((e) => notify(e.message, { type: 'error' })),
    [dataProvider, notify],
  );
  useEffect(() => {
    load();
  }, [load]);

  if (!stats) return <LinearProgress sx={{ m: 2 }} />;
  const byStatus = stats.batchesByStatus ?? {};
  const roles = Object.entries(stats.usersByRole ?? {})
    .map(([role, n]) => `${n} ${role.toLowerCase().replace('_', ' ')}`)
    .join(', ');

  return (
    <Box sx={{ p: 2 }}>
      <Title title="ChainVax admin console" />
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 2 }}>
        <Typography variant="h5">Overview</Typography>
        <Button label="Refresh" onClick={load}>
          <RefreshIcon />
        </Button>
      </Stack>
      {!stats.ledgerValid && (
        <Alert severity="error" sx={{ mb: 2 }}>
          The custody ledger failed verification. Open Custody ledger and use Verify chain for details.
        </Alert>
      )}
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(230px, 1fr))', gap: 2 }}>
        <StatCard title="Vaccine batches" value={stats.batches} to="/batches">
          <Stack direction="row" spacing={0.5} sx={{ mt: 1, flexWrap: 'wrap' }}>
            <Chip size="small" color="success" label={`VIABLE ${byStatus.VIABLE ?? 0}`} />
            <Chip size="small" color="warning" label={`AT RISK ${byStatus.AT_RISK ?? 0}`} />
            <Chip size="small" color="error" label={`CONDEMNED ${byStatus.CONDEMNED ?? 0}`} />
          </Stack>
        </StatCard>
        <StatCard title="Vials on hand" value={stats.vialsOnHand} to="/batches" />
        <StatCard title="Custody ledger entries" value={stats.ledgerEntries} to="/ledger">
          <Chip
            size="small"
            sx={{ mt: 1 }}
            color={stats.ledgerValid ? 'success' : 'error'}
            label={stats.ledgerValid ? `Chain valid, head ${shortHash(stats.ledgerHeadHash)}` : 'CHAIN BROKEN'}
          />
        </StatCard>
        <StatCard title="Open excursions" value={stats.openExcursions} to="/windows" />
        <StatCard title="User accounts" value={stats.users} to="/users">
          <Typography variant="body2" color="text.secondary">
            {roles}
          </Typography>
        </StatCard>
        <StatCard title="Sites" value={stats.sites} to="/sites" />
        <StatCard title="Logger devices" value={stats.loggers} to="/devices" />
        <StatCard title="Excursion policies" value={stats.policies} to="/policies" />
        <StatCard title="Temperature readings" value={stats.readings} to="/readings" />
        <StatCard title="Override audit records" value={stats.overrides} to="/overrides" />
        <StatCard title="Stock issues" value={stats.stockIssues} to="/issues" />
        <StatCard title="Alert digests" value={stats.digests} to="/digests" />
      </Box>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 3 }}>
        Database: {String(stats.database).split(' on ')[0]} | Cache: {stats.cacheProvider} | Profiles:{' '}
        {stats.activeProfiles?.length ? stats.activeProfiles.join(', ') : 'default (embedded)'}
      </Typography>
    </Box>
  );
};
