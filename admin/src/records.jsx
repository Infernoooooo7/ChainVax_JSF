// Read-only operational records. The admin can search, sort, filter and export them (CSV), but never
// edit them: these rows are evidence (ledger, readings, lifecycle, excursions, overrides, issues, digests).
import { Box, Typography } from '@mui/material';
import VerifiedIcon from '@mui/icons-material/Verified';
import {
  BooleanField,
  Button,
  Datagrid,
  DateField,
  ExportButton,
  FilterButton,
  List,
  NumberField,
  ReferenceField,
  SearchInput,
  SelectInput,
  TextField,
  TopToolbar,
  useDataProvider,
  useNotify,
  useRecordContext,
} from 'react-admin';
import { HashField, STATUSES, StatusField, shortHash } from './fields';

const search = [<SearchInput key="q" source="q" alwaysOn />];
const readOnly = { bulkActionButtons: false, rowClick: false };

const BatchLink = () => (
  <ReferenceField source="batchId" reference="batches" link="show" label="Batch" emptyText="-">
    <TextField source="batchCode" />
  </ReferenceField>
);

const Pre = ({ title, text }) => (
  <Box sx={{ mb: 1 }}>
    <Typography variant="caption" color="text.secondary">
      {title}
    </Typography>
    <Box component="pre" sx={{ m: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all', fontSize: 12 }}>
      {text}
    </Box>
  </Box>
);

// ------------------------------------------------------------------ custody ledger

const VerifyChainButton = () => {
  const dataProvider = useDataProvider();
  const notify = useNotify();
  const verify = () =>
    dataProvider
      .verifyLedger()
      .then((r) =>
        r.valid
          ? notify(`Chain valid: ${r.entriesChecked} entries re-hashed, head ${shortHash(r.headHash)}`, { type: 'success' })
          : notify(`CHAIN BROKEN: ${r.problems.length} problem(s). First: ${JSON.stringify(r.problems[0])}`, {
              type: 'error',
              autoHideDuration: 15000,
            }),
      )
      .catch((e) => notify(e.message, { type: 'error' }));
  return (
    <Button label="Verify chain" onClick={verify}>
      <VerifiedIcon />
    </Button>
  );
};

const LedgerActions = () => (
  <TopToolbar>
    <VerifyChainButton />
    <FilterButton />
    <ExportButton />
  </TopToolbar>
);

const LedgerDetail = () => {
  const record = useRecordContext();
  return (
    <Box sx={{ p: 1 }}>
      <Pre title="Canonical payload (hashed)" text={record.payload} />
      <Pre title="Previous hash" text={record.previousHash} />
      <Pre title="Hash = SHA-256(previous hash + payload)" text={record.hash} />
    </Box>
  );
};

export const LedgerList = () => (
  <List
    filters={[...search, <SelectInput key="t" source="eventType" choices={[{ id: 'REGISTERED', name: 'REGISTERED' }, { id: 'TRANSFER', name: 'TRANSFER' }]} />]}
    sort={{ field: 'id', order: 'DESC' }}
    actions={<LedgerActions />}
    title="Custody ledger (hash-chained)"
  >
    <Datagrid bulkActionButtons={false} rowClick="expand" expand={<LedgerDetail />}>
      <TextField source="id" label="Seq" />
      <TextField source="eventType" />
      <BatchLink />
      <TextField source="fromSiteCode" label="From" />
      <TextField source="toSiteCode" label="To" />
      <TextField source="toCustodian" label="Custodian" />
      <NumberField source="vials" />
      <StatusField source="batchStatus" label="Status" />
      <DateField source="occurredAt" showTime />
      <TextField source="recordedBy" />
      <HashField source="hash" />
    </Datagrid>
  </List>
);

// ------------------------------------------------------------------ other records

export const ReadingList = () => (
  <List
    filters={[...search, <SelectInput key="s" source="source" choices={[{ id: 'LOGGER', name: 'LOGGER' }, { id: 'SIMULATOR', name: 'SIMULATOR' }]} />]}
    sort={{ field: 'id', order: 'DESC' }}
    title="Temperature readings"
  >
    <Datagrid {...readOnly}>
      <TextField source="id" />
      <TextField source="deviceCode" label="Logger" />
      <TextField source="siteCode" label="Site" />
      <DateField source="recordedAt" showTime />
      <NumberField source="temperatureC" label="Temp (C)" />
      <TextField source="source" />
      <BooleanField source="late" />
      <DateField source="receivedAt" showTime />
    </Datagrid>
  </List>
);

export const EventList = () => (
  <List filters={[...search, <SelectInput key="t" source="toStatus" choices={STATUSES} />]} sort={{ field: 'id', order: 'DESC' }} title="Lifecycle events">
    <Datagrid {...readOnly}>
      <TextField source="id" />
      <BatchLink />
      <StatusField source="fromStatus" label="From" />
      <StatusField source="toStatus" label="To" />
      <TextField source="reason" />
      <TextField source="actor" />
      <TextField source="siteCode" label="Site" />
      <DateField source="occurredAt" showTime />
    </Datagrid>
  </List>
);

export const WindowList = () => (
  <List
    filters={[...search, <SelectInput key="o" source="open" choices={[{ id: true, name: 'Open' }, { id: false, name: 'Closed' }]} />]}
    sort={{ field: 'id', order: 'DESC' }}
    title="Excursion windows"
  >
    <Datagrid {...readOnly}>
      <TextField source="id" />
      <BatchLink />
      <TextField source="type" />
      <TextField source="siteCode" label="Site" />
      <TextField source="deviceCode" label="Logger" />
      <DateField source="startedAt" showTime />
      <DateField source="endedAt" showTime />
      <NumberField source="peakTemperatureC" label="Peak (C)" />
      <NumberField source="durationSeconds" label="Duration (s)" />
      <BooleanField source="open" />
      <StatusField source="outcome" />
      <TextField source="policyCode" label="Policy" />
    </Datagrid>
  </List>
);

const OVERRIDE_TYPES = ['FEFO_OVERRIDE', 'STATUS_RELEASE', 'POLICY_CHANGE'].map((id) => ({ id, name: id }));

export const OverrideList = () => (
  <List filters={[...search, <SelectInput key="t" source="type" choices={OVERRIDE_TYPES} />]} sort={{ field: 'id', order: 'DESC' }} title="Override audit">
    <Datagrid {...readOnly}>
      <TextField source="id" />
      <TextField source="type" />
      <BatchLink />
      <TextField source="officerUsername" label="By" />
      <TextField source="reason" />
      <TextField source="details" />
      <DateField source="createdAt" showTime />
    </Datagrid>
  </List>
);

export const IssueList = () => (
  <List filters={search} sort={{ field: 'id', order: 'DESC' }} title="Stock issues">
    <Datagrid {...readOnly}>
      <TextField source="id" />
      <TextField source="batchCode" />
      <TextField source="product" />
      <TextField source="siteCode" label="Site" />
      <NumberField source="vials" />
      <TextField source="issuedBy" />
      <DateField source="issuedAt" showTime />
      <BooleanField source="fefoOrder" label="FEFO order" />
      <TextField source="overrideAuditId" label="Override #" emptyText="-" />
      <TextField source="purpose" />
    </Datagrid>
  </List>
);

const DigestBody = () => <Pre title="Digest" text={useRecordContext().body} />;

export const DigestList = () => (
  <List filters={search} sort={{ field: 'id', order: 'DESC' }} title="Alert digests">
    <Datagrid bulkActionButtons={false} rowClick="expand" expand={<DigestBody />}>
      <TextField source="id" />
      <DateField source="generatedAt" showTime />
      <TextField source="triggerSource" label="Trigger" />
      <TextField source="headline" />
      <NumberField source="ledgerEntries" />
      <NumberField source="openExcursions" />
      <NumberField source="atRiskBatches" label="At risk" />
      <NumberField source="condemnedBatches" label="Condemned" />
      <NumberField source="expiringSoon" />
      <HashField source="ledgerHeadHash" label="Ledger head" />
    </Datagrid>
  </List>
);
