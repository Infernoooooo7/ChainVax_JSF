// ChainVax admin console, built on react-admin (MIT, github.com/marmelab/react-admin).
import { Box, Typography } from '@mui/material';
import PeopleIcon from '@mui/icons-material/People';
import StoreIcon from '@mui/icons-material/Store';
import SensorsIcon from '@mui/icons-material/Sensors';
import PolicyIcon from '@mui/icons-material/Policy';
import VaccinesIcon from '@mui/icons-material/Vaccines';
import LinkIcon from '@mui/icons-material/Link';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import TimelineIcon from '@mui/icons-material/Timeline';
import WarningIcon from '@mui/icons-material/WarningAmber';
import GavelIcon from '@mui/icons-material/Gavel';
import InventoryIcon from '@mui/icons-material/Inventory';
import SummarizeIcon from '@mui/icons-material/Summarize';
import { Admin, Login, LoginForm, Resource, bwDarkTheme, bwLightTheme } from 'react-admin';
import { deepmerge } from '@mui/utils';
import { authProvider, dataProvider } from './providers';
import { Dashboard } from './Dashboard';
import {
  DeviceCreate,
  DeviceEdit,
  DeviceList,
  PolicyCreate,
  PolicyEdit,
  PolicyList,
  SiteCreate,
  SiteEdit,
  SiteList,
  UserCreate,
  UserEdit,
  UserList,
} from './masterData';
import { BatchCreate, BatchEdit, BatchList, BatchShow } from './batches';
import { DigestList, EventList, IssueList, LedgerList, OverrideList, ReadingList, WindowList } from './records';

// Same look as the staff app: react-admin's built-in B&W theme already uses the Geist font and a neutral
// black/white palette, matching Watermelon UI's Medesk tokens; only the corner radius is aligned.
const lightTheme = deepmerge(bwLightTheme, { shape: { borderRadius: 8 } });
const darkTheme = deepmerge(bwDarkTheme, { shape: { borderRadius: 8 } });

const AdminLoginPage = () => (
  <Login>
    <Box sx={{ px: 3, textAlign: 'center' }}>
      <Typography variant="h6">ChainVax Admin Console</Typography>
      <Typography variant="body2" color="text.secondary">
        Administrators only. Staff sign in on the main ChainVax page.
      </Typography>
    </Box>
    <LoginForm />
  </Login>
);

export const App = () => (
  <Admin
    title="ChainVax Admin"
    dataProvider={dataProvider}
    authProvider={authProvider}
    dashboard={Dashboard}
    loginPage={AdminLoginPage}
    theme={lightTheme}
    darkTheme={darkTheme}
    defaultTheme="light"
    requireAuth
    disableTelemetry
  >
    <Resource name="users" options={{ label: 'User accounts' }} icon={PeopleIcon} list={UserList} create={UserCreate} edit={UserEdit} recordRepresentation="username" />
    <Resource name="sites" icon={StoreIcon} list={SiteList} create={SiteCreate} edit={SiteEdit} recordRepresentation="code" />
    <Resource name="devices" options={{ label: 'Logger devices' }} icon={SensorsIcon} list={DeviceList} create={DeviceCreate} edit={DeviceEdit} recordRepresentation="deviceCode" />
    <Resource name="policies" options={{ label: 'Excursion policies' }} icon={PolicyIcon} list={PolicyList} create={PolicyCreate} edit={PolicyEdit} recordRepresentation="code" />
    <Resource name="batches" options={{ label: 'Vaccine batches' }} icon={VaccinesIcon} list={BatchList} create={BatchCreate} edit={BatchEdit} show={BatchShow} recordRepresentation="batchCode" />
    <Resource name="ledger" options={{ label: 'Custody ledger' }} icon={LinkIcon} list={LedgerList} />
    <Resource name="readings" options={{ label: 'Temperature readings' }} icon={ThermostatIcon} list={ReadingList} />
    <Resource name="events" options={{ label: 'Lifecycle events' }} icon={TimelineIcon} list={EventList} />
    <Resource name="windows" options={{ label: 'Excursions' }} icon={WarningIcon} list={WindowList} />
    <Resource name="overrides" options={{ label: 'Override audit' }} icon={GavelIcon} list={OverrideList} />
    <Resource name="issues" options={{ label: 'Stock issues' }} icon={InventoryIcon} list={IssueList} />
    <Resource name="digests" options={{ label: 'Alert digests' }} icon={SummarizeIcon} list={DigestList} />
  </Admin>
);
