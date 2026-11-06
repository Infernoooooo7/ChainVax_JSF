// Vaccine batches: register (appends a REGISTERED ledger entry), correct details, and a full history view.
// Batches are never deleted - they are part of the hash-chained custody ledger.
import {
  BooleanField,
  Create,
  Datagrid,
  DateField,
  DateInput,
  Edit,
  EditButton,
  List,
  NumberField,
  NumberInput,
  ReferenceField,
  ReferenceInput,
  ReferenceManyField,
  SaveButton,
  SearchInput,
  SelectInput,
  Show,
  SimpleForm,
  TabbedShowLayout,
  TextField,
  TextInput,
  Toolbar,
  TopToolbar,
  minValue,
  regex,
  required,
} from 'react-admin';
import { HashField, STATUSES, StatusField } from './fields';

const batchFilters = [
  <SearchInput key="q" source="q" alwaysOn />,
  <SelectInput key="status" source="status" choices={STATUSES} />,
  <ReferenceInput key="siteId" source="siteId" reference="sites">
    <SelectInput label="Site" optionText="code" />
  </ReferenceInput>,
  <ReferenceInput key="policyId" source="policyId" reference="policies">
    <SelectInput label="Policy" optionText="code" />
  </ReferenceInput>,
];

export const BatchList = () => (
  <List filters={batchFilters} sort={{ field: 'expiryDate', order: 'ASC' }}>
    <Datagrid rowClick="show" bulkActionButtons={false}>
      <TextField source="batchCode" />
      <TextField source="product" />
      <TextField source="lotNumber" />
      <DateField source="expiryDate" />
      <NumberField source="vialsOnHand" label="Vials" />
      <StatusField />
      <ReferenceField source="siteId" reference="sites" label="Site">
        <TextField source="code" />
      </ReferenceField>
      <TextField source="custodian" />
      <ReferenceField source="policyId" reference="policies" label="Policy">
        <TextField source="code" />
      </ReferenceField>
      <BooleanField source="issuable" />
      <EditButton />
    </Datagrid>
  </List>
);

const PolicyPicker = () => (
  <ReferenceInput source="policyId" reference="policies">
    <SelectInput label="Excursion policy" optionText={(p) => `${p.code} - ${p.name}`} validate={required()} />
  </ReferenceInput>
);

export const BatchCreate = () => (
  <Create redirect="show">
    <SimpleForm warnWhenUnsavedChanges defaultValues={{ dosesPerVial: 10 }}>
      <TextInput source="batchCode" validate={[required(), regex(/^[A-Z0-9-]+$/, 'Upper-case letters, digits and - only')]} helperText="e.g. CVX-BCG-001" />
      <TextInput source="product" validate={required()} />
      <TextInput source="lotNumber" validate={required()} />
      <TextInput source="manufacturer" />
      <DateInput source="expiryDate" validate={required()} />
      <NumberInput source="dosesPerVial" validate={[required(), minValue(1)]} />
      <NumberInput source="initialVials" label="Vials received" validate={[required(), minValue(1)]} />
      <ReferenceInput source="siteId" reference="sites" filter={{ type: 'DISTRICT_STORE' }}>
        <SelectInput label="Received at (district store)" optionText={(s) => `${s.code} - ${s.name}`} validate={required()} />
      </ReferenceInput>
      <ReferenceInput source="custodianId" reference="users" filter={{ enabled: true }} sort={{ field: 'username', order: 'ASC' }}>
        <SelectInput
          label="Receiving custodian"
          optionText={(u) => `${u.username} (${u.role})`}
          validate={required()}
          helperText="The store's storekeeper or a district officer. Registration is written to the custody ledger."
        />
      </ReferenceInput>
      <PolicyPicker />
    </SimpleForm>
  </Create>
);

const SaveOnlyToolbar = () => (
  <Toolbar>
    <SaveButton />
  </Toolbar>
);

export const BatchEdit = () => (
  <Edit mutationMode="pessimistic" redirect="show">
    <SimpleForm toolbar={<SaveOnlyToolbar />} warnWhenUnsavedChanges>
      <TextInput source="batchCode" disabled helperText="Status, location, custodian and vials change only through the lifecycle, custody and stock flows." />
      <TextInput source="product" validate={required()} />
      <TextInput source="lotNumber" validate={required()} />
      <TextInput source="manufacturer" />
      <DateInput source="expiryDate" validate={required()} />
      <NumberInput source="dosesPerVial" validate={[required(), minValue(1)]} />
      <PolicyPicker />
    </SimpleForm>
  </Edit>
);

const BatchShowActions = () => (
  <TopToolbar>
    <EditButton label="Correct details" />
  </TopToolbar>
);

const history = (reference, children, target = 'batchId', source = 'id') => (
  <ReferenceManyField reference={reference} target={target} source={source} sort={{ field: 'id', order: 'ASC' }} perPage={100}>
    <Datagrid bulkActionButtons={false} rowClick={false} empty={<p>None.</p>}>
      {children}
    </Datagrid>
  </ReferenceManyField>
);

export const BatchShow = () => (
  <Show actions={<BatchShowActions />}>
    <TabbedShowLayout>
      <TabbedShowLayout.Tab label="Details">
        <TextField source="batchCode" />
        <TextField source="product" />
        <TextField source="lotNumber" />
        <TextField source="manufacturer" />
        <DateField source="expiryDate" />
        <StatusField />
        <TextField source="statusReason" />
        <NumberField source="vialsOnHand" />
        <NumberField source="initialVials" />
        <NumberField source="dosesPerVial" />
        <TextField source="siteCode" label="Current site" />
        <TextField source="custodian" label="Current custodian" />
        <TextField source="policyCode" label="Policy" />
        <BooleanField source="issuable" />
        <NumberField source="version" label="@Version" />
      </TabbedShowLayout.Tab>
      <TabbedShowLayout.Tab label="Custody ledger" path="ledger">
        {history('ledger', [
          <TextField key="id" source="id" label="Seq" />,
          <TextField key="t" source="eventType" />,
          <TextField key="f" source="fromSiteCode" label="From" />,
          <TextField key="to" source="toSiteCode" label="To" />,
          <TextField key="c" source="toCustodian" label="Custodian" />,
          <NumberField key="v" source="vials" />,
          <DateField key="at" source="occurredAt" showTime />,
          <TextField key="by" source="recordedBy" />,
          <HashField key="h" source="hash" />,
        ])}
      </TabbedShowLayout.Tab>
      <TabbedShowLayout.Tab label="Lifecycle" path="lifecycle">
        {history('events', [
          <StatusField key="f" source="fromStatus" label="From" />,
          <StatusField key="t" source="toStatus" label="To" />,
          <TextField key="r" source="reason" />,
          <TextField key="a" source="actor" />,
          <DateField key="at" source="occurredAt" showTime />,
        ])}
      </TabbedShowLayout.Tab>
      <TabbedShowLayout.Tab label="Excursions" path="excursions">
        {history('windows', [
          <TextField key="t" source="type" />,
          <TextField key="s" source="siteCode" />,
          <TextField key="d" source="deviceCode" />,
          <DateField key="st" source="startedAt" showTime />,
          <DateField key="en" source="endedAt" showTime />,
          <NumberField key="p" source="peakTemperatureC" label="Peak (C)" />,
          <NumberField key="du" source="durationSeconds" label="Duration (s)" />,
          <BooleanField key="o" source="open" />,
          <StatusField key="out" source="outcome" />,
        ])}
      </TabbedShowLayout.Tab>
      <TabbedShowLayout.Tab label="Overrides" path="overrides">
        {history('overrides', [
          <TextField key="t" source="type" />,
          <TextField key="o" source="officerUsername" label="Officer" />,
          <TextField key="r" source="reason" />,
          <TextField key="d" source="details" />,
          <DateField key="at" source="createdAt" showTime />,
        ])}
      </TabbedShowLayout.Tab>
      <TabbedShowLayout.Tab label="Stock issues" path="issues">
        {history(
          'issues',
          [
            <TextField key="s" source="siteCode" />,
            <NumberField key="v" source="vials" />,
            <TextField key="b" source="issuedBy" />,
            <DateField key="at" source="issuedAt" showTime />,
            <BooleanField key="f" source="fefoOrder" label="FEFO order" />,
            <TextField key="p" source="purpose" />,
          ],
          'batchCode',
          'batchCode',
        )}
      </TabbedShowLayout.Tab>
    </TabbedShowLayout>
  </Show>
);
