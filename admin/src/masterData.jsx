// CRUD screens for master data: user accounts, sites, logger devices, excursion policies.
import {
  BooleanField,
  BooleanInput,
  BulkDeleteButton,
  ChipField,
  Create,
  Datagrid,
  DateField,
  Edit,
  List,
  NumberField,
  NumberInput,
  PasswordInput,
  ReferenceField,
  ReferenceInput,
  SearchInput,
  SelectField,
  SelectInput,
  SimpleForm,
  TextField,
  TextInput,
  minLength,
  number,
  regex,
  required,
} from 'react-admin';
import { ROLES, SITE_TYPES } from './fields';

const pessimisticBulkDelete = <BulkDeleteButton mutationMode="pessimistic" />;
const ACTIVE = [
  { id: true, name: 'Active' },
  { id: false, name: 'Disabled' },
];
const upperCode = regex(/^[A-Z0-9-]+$/, 'Upper-case letters, digits and - only');

// ------------------------------------------------------------------ user accounts

const userFilters = [
  <SearchInput key="q" source="q" alwaysOn />,
  <SelectInput key="role" source="role" choices={ROLES} />,
  <SelectInput key="enabled" source="enabled" choices={ACTIVE} />,
];

export const UserList = () => (
  <List filters={userFilters} sort={{ field: 'id', order: 'ASC' }}>
    <Datagrid rowClick="edit" bulkActionButtons={pessimisticBulkDelete}>
      <TextField source="id" />
      <TextField source="username" />
      <TextField source="fullName" />
      <ChipField source="role" size="small" />
      <ReferenceField source="siteId" reference="sites" label="Site" emptyText="district-wide">
        <TextField source="code" />
      </ReferenceField>
      <BooleanField source="enabled" label="Active" />
    </Datagrid>
  </List>
);

const UserForm = ({ create }) => (
  <SimpleForm warnWhenUnsavedChanges>
    <TextInput
      source="username"
      disabled={!create}
      validate={create ? [required(), regex(/^[a-z0-9._-]+$/, 'Lower-case letters, digits, . _ - only')] : undefined}
      helperText={create ? 'Login name. It cannot be changed later.' : 'Usernames are permanent (they appear in audit history).'}
    />
    <TextInput source="fullName" validate={required()} />
    <SelectInput source="role" choices={ROLES} validate={required()} />
    <ReferenceInput source="siteId" reference="sites" sort={{ field: 'code', order: 'ASC' }}>
      <SelectInput
        label="Assigned site"
        optionText={(site) => `${site.code} - ${site.name}`}
        parse={(value) => (value === '' ? null : value)}
        emptyText="(district-wide)"
        helperText="Required for storekeepers and transporters. Officers, auditors and admins are district-wide."
      />
    </ReferenceInput>
    <BooleanInput source="enabled" label="Active (can sign in)" defaultValue />
    <PasswordInput
      source="password"
      validate={create ? [required(), minLength(8)] : minLength(8)}
      helperText={create ? 'At least 8 characters. Stored only as a BCrypt hash.' : 'Leave blank to keep the current password.'}
    />
  </SimpleForm>
);

export const UserCreate = () => (
  <Create redirect="list">
    <UserForm create />
  </Create>
);

export const UserEdit = () => (
  <Edit mutationMode="pessimistic" redirect="list">
    <UserForm />
  </Edit>
);

// ------------------------------------------------------------------ sites

export const SiteList = () => (
  <List filters={[<SearchInput key="q" source="q" alwaysOn />, <SelectInput key="type" source="type" choices={SITE_TYPES} />]}>
    <Datagrid rowClick="edit" bulkActionButtons={pessimisticBulkDelete}>
      <TextField source="id" />
      <TextField source="code" />
      <TextField source="name" />
      <SelectField source="type" choices={SITE_TYPES} />
      <TextField source="district" />
      <NumberField source="batches" label="Batches here" />
      <NumberField source="staff" />
      <NumberField source="loggers" />
    </Datagrid>
  </List>
);

const SiteForm = ({ create }) => (
  <SimpleForm warnWhenUnsavedChanges>
    <TextInput
      source="code"
      disabled={!create}
      validate={create ? [required(), upperCode] : undefined}
      helperText={create ? 'e.g. PHC-KRK. It cannot be changed later.' : 'Site codes are part of the signed ledger history.'}
    />
    <TextInput source="name" validate={required()} helperText='Add "(demo)" for demo sites.' />
    <SelectInput source="type" choices={SITE_TYPES} validate={required()} />
    <TextInput source="district" validate={required()} />
  </SimpleForm>
);

export const SiteCreate = () => (
  <Create redirect="list">
    <SiteForm create />
  </Create>
);

export const SiteEdit = () => (
  <Edit mutationMode="pessimistic" redirect="list">
    <SiteForm />
  </Edit>
);

// ------------------------------------------------------------------ logger devices

export const DeviceList = () => (
  <List filters={[<SearchInput key="q" source="q" alwaysOn />]}>
    <Datagrid rowClick="edit" bulkActionButtons={pessimisticBulkDelete}>
      <TextField source="id" />
      <TextField source="deviceCode" label="Logger" />
      <ReferenceField source="siteId" reference="sites" label="Site">
        <TextField source="code" />
      </ReferenceField>
      <TextField source="description" />
      <DateField source="lastReadingAt" showTime label="Last reading" />
      <NumberField source="lastTemperatureC" label="Last temp (C)" />
    </Datagrid>
  </List>
);

const DeviceForm = ({ create }) => (
  <SimpleForm warnWhenUnsavedChanges>
    <TextInput
      source="deviceCode"
      label="Logger code"
      disabled={!create}
      validate={create ? [required(), upperCode] : undefined}
      helperText={create ? 'e.g. LOG-PHC-KRK. It cannot be changed later.' : 'Stored readings reference this code.'}
    />
    <ReferenceInput source="siteId" reference="sites" sort={{ field: 'code', order: 'ASC' }}>
      <SelectInput label="Installed at" optionText={(site) => `${site.code} - ${site.name}`} validate={required()} />
    </ReferenceInput>
    <TextInput source="description" fullWidth />
    <PasswordInput
      source="secret"
      label={create ? 'Device secret' : 'New device secret'}
      validate={create ? [required(), minLength(8)] : minLength(8)}
      helperText={
        create
          ? 'The logger uses this to get a device token. Stored only as a BCrypt hash.'
          : 'Leave blank to keep the current secret. Fill in to rotate it.'
      }
    />
  </SimpleForm>
);

export const DeviceCreate = () => (
  <Create redirect="list">
    <DeviceForm create />
  </Create>
);

export const DeviceEdit = () => (
  <Edit mutationMode="pessimistic" redirect="list">
    <DeviceForm />
  </Edit>
);

// ------------------------------------------------------------------ excursion policies

export const PolicyList = () => (
  <List filters={[<SearchInput key="q" source="q" alwaysOn />]}>
    <Datagrid rowClick="edit" bulkActionButtons={pessimisticBulkDelete}>
      <TextField source="code" />
      <TextField source="name" />
      <BooleanField source="demoOnly" label="Demo only" />
      <NumberField source="heatAlarmC" label="Heat alarm (C)" />
      <NumberField source="coldAlarmC" label="Cold alarm (C)" />
      <NumberField source="atRiskAfterSeconds" label="At-risk after (s)" />
      <NumberField source="heatCondemnAfterSeconds" label="Heat condemn (s)" />
      <NumberField source="coldCondemnAfterSeconds" label="Cold condemn (s)" />
      <NumberField source="version" />
      <TextField source="updatedBy" />
      <DateField source="updatedAt" showTime />
    </Datagrid>
  </List>
);

const PolicyForm = ({ create }) => (
  <SimpleForm warnWhenUnsavedChanges>
    <TextInput
      source="code"
      disabled={!create}
      validate={create ? [required(), upperCode] : undefined}
      helperText={create ? 'e.g. PQS-E006-T2' : 'Batches reference this code.'}
    />
    <TextInput source="name" validate={required()} fullWidth />
    <TextInput source="basis" validate={required()} multiline fullWidth helperText="Where the thresholds come from (cite the standard, or say it is a demo choice)." />
    <BooleanInput source="demoOnly" label="Demo only (accelerated timings)" />
    <NumberInput source="heatAlarmC" label="Heat alarm at or above (C)" step={0.1} validate={[required(), number()]} />
    <NumberInput source="coldAlarmC" label="Cold alarm at or below (C)" step={0.1} validate={[required(), number()]} />
    <NumberInput source="atRiskAfterSeconds" label="AT_RISK after (seconds)" min={0} validate={required()} />
    <NumberInput source="heatCondemnAfterSeconds" label="Heat excursion condemns after (seconds)" min={1} validate={required()} />
    <NumberInput source="coldCondemnAfterSeconds" label="Cold excursion condemns after (seconds)" min={1} validate={required()} />
    {!create && (
      <TextInput
        source="reason"
        label="Reason for the change"
        multiline
        fullWidth
        validate={[required(), minLength(10)]}
        helperText="Recorded as a POLICY_CHANGE entry in the override audit (before/after thresholds)."
      />
    )}
  </SimpleForm>
);

export const PolicyCreate = () => (
  <Create redirect="list">
    <PolicyForm create />
  </Create>
);

export const PolicyEdit = () => (
  <Edit mutationMode="pessimistic" redirect="list">
    <PolicyForm />
  </Edit>
);
