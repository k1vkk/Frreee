import React, { useCallback, useEffect, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Database,
  History,
  LogOut,
  Plus,
  Power,
  RefreshCw,
  Server,
  Shield,
  Trash2,
  Users
} from 'lucide-react';
import { apiRequest, formatBytes, resolveDefaultApiUrl } from './api.js';

const COPY = {
  ar: {
    title: 'لوحة تحكم NEXORA VPN',
    subtitle: 'إدارة WireGuard والخوادم الخليجية والمستخدمين والاشتراكات',
    apiUrl: 'رابط Backend API (HTTPS)',
    email: 'بريد المشرف',
    password: 'كلمة المرور',
    adminKey: 'أو مفتاح الإدارة (ADMIN_SECRET)',
    signIn: 'دخول لوحة التحكم',
    signOut: 'تسجيل الخروج',
    refresh: 'تحديث فوري',
    totalUsers: 'إجمالي المستخدمين',
    connectedUsers: 'المستخدمين المتصلين',
    serverHealth: 'حالة السيرفرات',
    serverLoad: 'ضغط السيرفرات',
    networkUsage: 'استهلاك الشبكة',
    proSubs: 'الاشتراكات المدفوعة',
    unresolvedErrors: 'أخطاء قيد المتابعة',
    tabServers: 'السيرفرات وضغط الشبكة',
    tabUsers: 'المستخدمين والاشتراكات',
    tabConnections: 'سجل الاتصالات',
    tabErrors: 'مراقبة الأخطاء',
    addServer: 'إضافة سيرفر WireGuard جديد',
    createServer: 'حفظ وتشغيل السيرفر',
    enable: 'تفعيل',
    disable: 'تعطيل',
    delete: 'حذف',
    searchUsers: 'بحث بالبريد أو الاسم...',
    resolve: 'تمت المعالجة',
    noErrors: 'لا توجد أخطاء مسجلة في PostgreSQL.',
    noUsers: 'لا يوجد مستخدمون مسجلون بعد.',
    noConnections: 'لا توجد جلسات اتصال مسجلة في connection_logs.',
    langSwitch: 'English (LTR)'
  },
  en: {
    title: 'NEXORA VPN Control Center',
    subtitle: 'Live WireGuard GCC Nodes, PostgreSQL Users, Billing & Telemetry',
    apiUrl: 'Backend API Base URL (HTTPS)',
    email: 'Admin Email',
    password: 'Password',
    adminKey: 'Or ADMIN_SECRET',
    signIn: 'Sign In to Dashboard',
    signOut: 'Sign Out',
    refresh: 'Refresh',
    totalUsers: 'Total Users',
    connectedUsers: 'Connected Users',
    serverHealth: 'Server Health',
    serverLoad: 'Avg Server Load',
    networkUsage: 'Network Usage',
    proSubs: 'Active Pro Plans',
    unresolvedErrors: 'Unresolved Errors',
    tabServers: 'Servers & Load',
    tabUsers: 'Users & Subscriptions',
    tabConnections: 'Connection Logs',
    tabErrors: 'Error Monitor',
    addServer: 'Add New WireGuard Server',
    createServer: 'Create & Probe Server',
    enable: 'Enable',
    disable: 'Disable',
    delete: 'Delete',
    searchUsers: 'Search email or name...',
    resolve: 'Resolve',
    noErrors: 'No errors recorded in PostgreSQL.',
    noUsers: 'No registered users yet.',
    noConnections: 'No VPN sessions logged yet.',
    langSwitch: 'العربية (RTL)'
  }
};

export default function App() {
  const [lang, setLang] = useState('ar');
  const t = COPY[lang];

  const [apiUrl, setApiUrl] = useState(resolveDefaultApiUrl);
  const [token, setToken] = useState(() => localStorage.getItem('nexora_admin_jwt') || '');
  const [adminKey, setAdminKey] = useState(() => sessionStorage.getItem('nexora_admin_key') || '');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [keyInput, setKeyInput] = useState('');
  const [authError, setAuthError] = useState('');

  const [activeTab, setActiveTab] = useState('servers');
  const [overview, setOverview] = useState(null);
  const [servers, setServers] = useState([]);
  const [users, setUsers] = useState([]);
  const [connections, setConnections] = useState([]);
  const [errors, setErrors] = useState([]);
  const [userSearch, setUserSearch] = useState('');
  const [busy, setBusy] = useState(false);
  const [bannerError, setBannerError] = useState('');

  const [newNode, setNewNode] = useState({
    nodeCode: '',
    name: '',
    countryName: '',
    countryCode: '',
    city: '',
    publicIp: '',
    serverPort: 51820,
    endpoint: '',
    serverPublicKey: '',
    agentUrl: '',
    ipv4Subnet: '10.8.20.0/24',
    ipv6Subnet: 'fd80:4e58:5220::/64',
    tier: 'free',
    maxPeers: 250
  });

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
  }, [lang]);

  const isAuthenticated = Boolean(token || adminKey);

  const loadAll = useCallback(async () => {
    if (!isAuthenticated || !apiUrl) return;
    setBusy(true);
    setBannerError('');
    try {
      const creds = { token, adminKey };
      const [ov, srv, usr, connRes, errRes] = await Promise.all([
        apiRequest(apiUrl, '/api/v1/admin/overview', creds),
        apiRequest(apiUrl, '/api/v1/admin/servers', creds),
        apiRequest(apiUrl, `/api/v1/admin/users?q=${encodeURIComponent(userSearch)}`, creds),
        apiRequest(apiUrl, '/api/v1/admin/connections', creds),
        apiRequest(apiUrl, '/api/v1/admin/errors', creds)
      ]);
      setOverview(ov);
      setServers(srv.servers || []);
      setUsers(usr.users || []);
      setConnections(connRes.connections || []);
      setErrors(errRes.errors || []);
    } catch (e) {
      setBannerError(e.message);
      if (e.message.includes('401') || e.message.includes('403')) {
        setToken('');
        setAdminKey('');
        localStorage.removeItem('nexora_admin_jwt');
        sessionStorage.removeItem('nexora_admin_key');
      }
    } finally {
      setBusy(false);
    }
  }, [apiUrl, token, adminKey, isAuthenticated, userSearch]);

  useEffect(() => {
    if (isAuthenticated) {
      loadAll();
      const timer = setInterval(loadAll, 20000);
      return () => clearInterval(timer);
    }
  }, [isAuthenticated, loadAll]);

  async function handleLogin(e) {
    e.preventDefault();
    setAuthError('');
    const cleanUrl = apiUrl.trim().replace(/\/+$/, '');
    localStorage.setItem('nexora_admin_api_url', cleanUrl);
    try {
      if (keyInput.trim()) {
        await apiRequest(cleanUrl, '/api/v1/admin/overview', { adminKey: keyInput.trim() });
        sessionStorage.setItem('nexora_admin_key', keyInput.trim());
        setAdminKey(keyInput.trim());
        return;
      }
      const res = await apiRequest(cleanUrl, '/api/v1/admin/login', {
        method: 'POST',
        body: { email, password }
      });
      localStorage.setItem('nexora_admin_jwt', res.token);
      setToken(res.token);
    } catch (err) {
      setAuthError(err.message);
    }
  }

  function handleLogout() {
    setToken('');
    setAdminKey('');
    localStorage.removeItem('nexora_admin_jwt');
    sessionStorage.removeItem('nexora_admin_key');
  }

  async function handleCreateServer(e) {
    e.preventDefault();
    setBannerError('');
    try {
      await apiRequest(apiUrl, '/api/v1/admin/servers', {
        method: 'POST',
        token,
        adminKey,
        body: {
          ...newNode,
          serverPort: Number(newNode.serverPort || 51820),
          maxPeers: Number(newNode.maxPeers || 250)
        }
      });
      setNewNode({
        nodeCode: '',
        name: '',
        countryName: '',
        countryCode: '',
        city: '',
        publicIp: '',
        serverPort: 51820,
        endpoint: '',
        serverPublicKey: '',
        agentUrl: '',
        ipv4Subnet: '10.8.20.0/24',
        ipv6Subnet: 'fd80:4e58:5220::/64',
        tier: 'free',
        maxPeers: 250
      });
      await loadAll();
    } catch (err) {
      setBannerError(err.message);
    }
  }

  async function handleToggleServerActive(server) {
    try {
      await apiRequest(apiUrl, `/api/v1/admin/servers/${server.id}`, {
        method: 'PATCH',
        token,
        adminKey,
        body: { isActive: !server.isActive }
      });
      await loadAll();
    } catch (err) {
      setBannerError(err.message);
    }
  }

  async function handleDeleteServer(id) {
    try {
      await apiRequest(apiUrl, `/api/v1/admin/servers/${id}`, {
        method: 'DELETE',
        token,
        adminKey
      });
      await loadAll();
    } catch (err) {
      setBannerError(err.message);
    }
  }

  async function handleUpdateUser(userId, patch) {
    try {
      await apiRequest(apiUrl, `/api/v1/admin/users/${userId}`, {
        method: 'PATCH',
        token,
        adminKey,
        body: patch
      });
      await loadAll();
    } catch (err) {
      setBannerError(err.message);
    }
  }

  async function handleResolveError(id) {
    try {
      await apiRequest(apiUrl, `/api/v1/admin/errors/${id}/resolve`, {
        method: 'PATCH',
        token,
        adminKey
      });
      await loadAll();
    } catch (err) {
      setBannerError(err.message);
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="login-shell">
        <form className="login-card" onSubmit={handleLogin}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <div className="brand">
              <div className="brand-badge">NX</div>
              <div>
                <h2 style={{ margin: 0 }}>{t.title}</h2>
                <div className="muted" style={{ fontSize: 12 }}>{t.subtitle}</div>
              </div>
            </div>
            <button type="button" className="btn" onClick={() => setLang(lang === 'ar' ? 'en' : 'ar')}>
              {t.langSwitch}
            </button>
          </div>

          <div style={{ display: 'grid', gap: 12 }}>
            <div>
              <label className="kpi-label" style={{ display: 'block' }}>{t.apiUrl}</label>
              <input
                className="input mono"
                placeholder="https://api.yourdomain.com"
                value={apiUrl}
                onChange={(e) => setApiUrl(e.target.value)}
                required
              />
            </div>
            <div>
              <label className="kpi-label" style={{ display: 'block' }}>{t.email}</label>
              <input
                className="input"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div>
              <label className="kpi-label" style={{ display: 'block' }}>{t.password}</label>
              <input
                className="input"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            <div>
              <label className="kpi-label" style={{ display: 'block' }}>{t.adminKey}</label>
              <input
                className="input mono"
                type="password"
                value={keyInput}
                onChange={(e) => setKeyInput(e.target.value)}
              />
            </div>
            {authError && <div style={{ color: 'var(--crimson)', fontSize: 13 }}>{authError}</div>}
            <button className="btn btn-primary" type="submit" style={{ justifyContent: 'center' }}>
              <Shield size={16} />
              {t.signIn}
            </button>
          </div>
        </form>
      </div>
    );
  }

  return (
    <div className="container">
      <header className="topbar">
        <div className="brand">
          <div className="brand-badge">NX</div>
          <div>
            <div style={{ fontWeight: 800, fontSize: 18 }}>{t.title}</div>
            <div className="muted mono" style={{ fontSize: 12 }}>{apiUrl}</div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button className="btn" onClick={() => setLang(lang === 'ar' ? 'en' : 'ar')}>
            {t.langSwitch}
          </button>
          <button className="btn" onClick={loadAll} disabled={busy}>
            <RefreshCw size={16} />
            {t.refresh}
          </button>
          <button className="btn btn-danger" onClick={handleLogout}>
            <LogOut size={16} />
            {t.signOut}
          </button>
        </div>
      </header>

      {bannerError && (
        <div className="card" style={{ borderColor: 'var(--crimson)', color: 'var(--crimson)', marginBottom: 16 }}>
          {bannerError}
        </div>
      )}

      {overview && (
        <section className="grid-kpi">
          <div className="card">
            <div className="kpi-label"><Users size={14} /> {t.totalUsers}</div>
            <div className="kpi-val">{overview.users.total}</div>
          </div>
          <div className="card">
            <div className="kpi-label"><Activity size={14} /> {t.connectedUsers}</div>
            <div className="kpi-val" style={{ color: 'var(--emerald)' }}>{overview.users.connected}</div>
          </div>
          <div className="card">
            <div className="kpi-label"><Server size={14} /> {t.serverHealth}</div>
            <div className="kpi-val" style={{ color: 'var(--cyan)' }}>
              {overview.servers.online} / {overview.servers.total}
            </div>
          </div>
          <div className="card">
            <div className="kpi-label">{t.serverLoad}</div>
            <div className="kpi-val">{overview.servers.avgLoadPercent}%</div>
          </div>
          <div className="card">
            <div className="kpi-label"><Database size={14} /> {t.networkUsage}</div>
            <div className="kpi-val" style={{ fontSize: 20 }}>
              {formatBytes(overview.network.totalRxBytes + overview.network.totalTxBytes)}
            </div>
          </div>
          <div className="card">
            <div className="kpi-label">{t.proSubs}</div>
            <div className="kpi-val" style={{ color: 'var(--blue)' }}>{overview.subscriptions.proActive}</div>
          </div>
          <div className="card">
            <div className="kpi-label"><AlertTriangle size={14} /> {t.unresolvedErrors}</div>
            <div className="kpi-val" style={{ color: overview.errors.unresolved > 0 ? 'var(--crimson)' : 'var(--emerald)' }}>
              {overview.errors.unresolved}
            </div>
          </div>
        </section>
      )}

      <nav className="tabs">
        <button
          className={`btn ${activeTab === 'servers' ? 'btn-tab-active' : ''}`}
          onClick={() => setActiveTab('servers')}
        >
          <Server size={16} />
          {t.tabServers}
        </button>
        <button
          className={`btn ${activeTab === 'users' ? 'btn-tab-active' : ''}`}
          onClick={() => setActiveTab('users')}
        >
          <Users size={16} />
          {t.tabUsers}
        </button>
        <button
          className={`btn ${activeTab === 'connections' ? 'btn-tab-active' : ''}`}
          onClick={() => setActiveTab('connections')}
        >
          <History size={16} />
          {t.tabConnections}
        </button>
        <button
          className={`btn ${activeTab === 'errors' ? 'btn-tab-active' : ''}`}
          onClick={() => setActiveTab('errors')}
        >
          <AlertTriangle size={16} />
          {t.tabErrors}
        </button>
      </nav>

      {activeTab === 'servers' && (
        <div style={{ display: 'grid', gap: 16 }}>
          <form className="card" onSubmit={handleCreateServer}>
            <h3 style={{ marginTop: 0 }}>{t.addServer}</h3>
            <div className="form-grid">
              <input className="input" placeholder="Code (e.g. SA03)" value={newNode.nodeCode} onChange={(e) => setNewNode({ ...newNode, nodeCode: e.target.value })} required />
              <input className="input" placeholder="Name (e.g. Dammam SA03)" value={newNode.name} onChange={(e) => setNewNode({ ...newNode, name: e.target.value })} required />
              <input className="input" placeholder="Country (Saudi Arabia)" value={newNode.countryName} onChange={(e) => setNewNode({ ...newNode, countryName: e.target.value })} required />
              <input className="input" placeholder="ISO (SA)" maxLength={2} value={newNode.countryCode} onChange={(e) => setNewNode({ ...newNode, countryCode: e.target.value })} required />
              <input className="input" placeholder="City (Dammam)" value={newNode.city} onChange={(e) => setNewNode({ ...newNode, city: e.target.value })} required />
              <input className="input mono" placeholder="Public IP (e.g. 203.0.113.10)" value={newNode.publicIp} onChange={(e) => setNewNode({ ...newNode, publicIp: e.target.value })} />
              <input className="input mono" type="number" placeholder="WG Port (51820)" value={newNode.serverPort} onChange={(e) => setNewNode({ ...newNode, serverPort: e.target.value })} />
              <input className="input mono" placeholder="Endpoint (ip:51820)" value={newNode.endpoint} onChange={(e) => setNewNode({ ...newNode, endpoint: e.target.value })} />
              <input className="input mono" placeholder="Server Public Key" value={newNode.serverPublicKey} onChange={(e) => setNewNode({ ...newNode, serverPublicKey: e.target.value })} />
              <input className="input mono" placeholder="Agent URL (http://ip:51821)" value={newNode.agentUrl} onChange={(e) => setNewNode({ ...newNode, agentUrl: e.target.value })} />
              <input className="input mono" placeholder="IPv4 Subnet" value={newNode.ipv4Subnet} onChange={(e) => setNewNode({ ...newNode, ipv4Subnet: e.target.value })} />
              <input className="input mono" placeholder="IPv6 Subnet" value={newNode.ipv6Subnet} onChange={(e) => setNewNode({ ...newNode, ipv6Subnet: e.target.value })} />
              <select className="select" value={newNode.tier} onChange={(e) => setNewNode({ ...newNode, tier: e.target.value })}>
                <option value="free">free</option>
                <option value="pro">pro</option>
              </select>
              <input className="input" type="number" placeholder="Max Peers" value={newNode.maxPeers} onChange={(e) => setNewNode({ ...newNode, maxPeers: e.target.value })} />
            </div>
            <div style={{ marginTop: 12 }}>
              <button className="btn btn-primary" type="submit">
                <Plus size={16} />
                {t.createServer}
              </button>
            </div>
          </form>

          <div className="card table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Server</th>
                  <th>Status</th>
                  <th>Load</th>
                  <th>CPU / RAM</th>
                  <th>Peers</th>
                  <th>Ping</th>
                  <th>Traffic (Rx / Tx)</th>
                  <th>Endpoint</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {servers.map((s) => (
                  <tr key={s.id}>
                    <td className="mono" style={{ color: 'var(--cyan)', fontWeight: 700 }}>{s.nodeCode}</td>
                    <td>{s.countryName} • {s.name}</td>
                    <td>
                      <span className={`badge ${!s.isActive ? 'badge-offline' : s.healthStatus === 'online' ? 'badge-online' : s.healthStatus === 'pending_vps' ? 'badge-pending' : 'badge-offline'}`}>
                        {!s.isActive ? 'disabled' : s.healthStatus}
                      </span>
                    </td>
                    <td>
                      <span className="progress-bar">
                        <span className="progress-fill" style={{ width: `${s.loadPercent}%` }} />
                      </span>
                      <span className="mono">{s.loadPercent}%</span>
                    </td>
                    <td className="mono">{Number(s.cpuUsage || 0).toFixed(0)}% / {Number(s.ramUsage || 0).toFixed(0)}%</td>
                    <td className="mono">{s.connectedPeers} / {s.maxPeers}</td>
                    <td className="mono">{s.latencyMs ? `${s.latencyMs} ms` : '—'}</td>
                    <td className="mono">{formatBytes(s.totalRxBytes)} / {formatBytes(s.totalTxBytes)}</td>
                    <td className="mono">{s.endpoint || 'pending_vps'}</td>
                    <td style={{ display: 'flex', gap: 6 }}>
                      <button className="btn" onClick={() => handleToggleServerActive(s)}>
                        <Power size={14} />
                        {s.isActive ? t.disable : t.enable}
                      </button>
                      <button className="btn btn-danger" onClick={() => handleDeleteServer(s.id)}>
                        <Trash2 size={14} />
                        {t.delete}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'users' && (
        <div className="card">
          <div style={{ marginBottom: 14, maxWidth: 360 }}>
            <input
              className="input"
              placeholder={t.searchUsers}
              value={userSearch}
              onChange={(e) => setUserSearch(e.target.value)}
            />
          </div>
          <div className="table-wrap">
            {users.length === 0 ? (
              <div className="muted">{t.noUsers}</div>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Email</th>
                    <th>Role</th>
                    <th>Plan</th>
                    <th>VPN Active</th>
                    <th>Traffic</th>
                    <th>State</th>
                    <th>Manage</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((u) => (
                    <tr key={u.id}>
                      <td>
                        <div style={{ fontWeight: 700 }}>{u.displayName}</div>
                        <div className="muted mono" style={{ fontSize: 12 }}>{u.email}</div>
                      </td>
                      <td>
                        <select
                          className="select"
                          value={u.role}
                          onChange={(e) => handleUpdateUser(u.id, { role: e.target.value })}
                        >
                          <option value="user">user</option>
                          <option value="moderator">moderator</option>
                          <option value="admin">admin</option>
                        </select>
                      </td>
                      <td>
                        <select
                          className="select"
                          value={u.planTier}
                          onChange={(e) => handleUpdateUser(u.id, { planTier: e.target.value, subscriptionStatus: 'active' })}
                        >
                          <option value="free">free</option>
                          <option value="pro_monthly">pro_monthly</option>
                          <option value="pro_annual">pro_annual</option>
                          <option value="pro_premium">pro_premium</option>
                        </select>
                      </td>
                      <td className="mono">{u.activeConnections}</td>
                      <td className="mono">{formatBytes(u.rxBytes + u.txBytes)}</td>
                      <td>
                        <span className={`badge ${u.isSuspended ? 'badge-offline' : 'badge-online'}`}>
                          {u.isSuspended ? 'suspended' : 'active'}
                        </span>
                      </td>
                      <td>
                        <button
                          className={`btn ${u.isSuspended ? '' : 'btn-danger'}`}
                          onClick={() => handleUpdateUser(u.id, { isSuspended: !u.isSuspended })}
                        >
                          {u.isSuspended ? 'Unsuspend' : 'Suspend'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}

      {activeTab === 'connections' && (
        <div className="card table-wrap">
          {connections.length === 0 ? (
            <div className="muted">{t.noConnections}</div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>User</th>
                  <th>Node</th>
                  <th>Assigned IPs</th>
                  <th>Duration</th>
                  <th>Traffic (Rx / Tx)</th>
                  <th>Status / Reason</th>
                  <th>Connected At</th>
                </tr>
              </thead>
              <tbody>
                {connections.map((c) => (
                  <tr key={c.id}>
                    <td>{c.userEmail}</td>
                    <td className="mono" style={{ color: 'var(--cyan)' }}>{c.nodeCode}</td>
                    <td className="mono">{c.assignedIpv4 || '—'}</td>
                    <td className="mono">{c.durationSeconds}s</td>
                    <td className="mono">{formatBytes(c.rxBytes)} / {formatBytes(c.txBytes)}</td>
                    <td>
                      <span className={`badge ${c.disconnectReason === 'active' ? 'badge-online' : 'badge-pending'}`}>
                        {c.disconnectReason}
                      </span>
                    </td>
                    <td className="mono">{new Date(c.connectedAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {activeTab === 'errors' && (
        <div className="card table-wrap">
          {errors.length === 0 ? (
            <div className="muted">{t.noErrors}</div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Level</th>
                  <th>Source</th>
                  <th>Node</th>
                  <th>Message</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {errors.map((err) => (
                  <tr key={err.id}>
                    <td className="mono">{new Date(err.createdAt).toLocaleString()}</td>
                    <td>
                      <span className={`badge ${err.level === 'critical' || err.level === 'error' ? 'badge-offline' : 'badge-pending'}`}>
                        {err.level}
                      </span>
                    </td>
                    <td className="mono">{err.source}</td>
                    <td className="mono">{err.nodeCode || '—'}</td>
                    <td style={{ whiteSpace: 'normal', minWidth: 260 }}>{err.message}</td>
                    <td>
                      {err.resolved ? (
                        <span className="badge badge-online"><CheckCircle2 size={12} /> Resolved</span>
                      ) : (
                        <button className="btn" onClick={() => handleResolveError(err.id)}>{t.resolve}</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}
