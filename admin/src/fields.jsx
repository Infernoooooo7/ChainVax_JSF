// Small shared fields built from react-admin's FunctionField + MUI Chip.
import { Chip } from '@mui/material';
import { FunctionField } from 'react-admin';

const STATUS_COLOR = { VIABLE: 'success', AT_RISK: 'warning', CONDEMNED: 'error' };

export const StatusField = ({ source = 'status', label }) => (
  <FunctionField
    source={source}
    label={label}
    render={(record) =>
      record?.[source] ? <Chip size="small" label={record[source]} color={STATUS_COLOR[record[source]] ?? 'default'} /> : null
    }
  />
);

export const shortHash = (hash) => (hash ? `${hash.slice(0, 12)}...` : '');

export const HashField = ({ source, label }) => (
  <FunctionField
    source={source}
    label={label}
    sortable={false}
    render={(record) => <code title={record?.[source]}>{shortHash(record?.[source])}</code>}
  />
);

export const ROLES = ['STOREKEEPER', 'TRANSPORTER', 'DISTRICT_OFFICER', 'AUDITOR', 'ADMIN'].map((id) => ({
  id,
  name: id.replace('_', ' '),
}));

export const SITE_TYPES = [
  { id: 'DISTRICT_STORE', name: 'District store' },
  { id: 'TRANSPORT_VAN', name: 'Transport van' },
  { id: 'HEALTH_CENTRE', name: 'Health centre' },
];

export const STATUSES = ['VIABLE', 'AT_RISK', 'CONDEMNED'].map((id) => ({ id, name: id }));
