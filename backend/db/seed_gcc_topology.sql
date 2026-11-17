-- GCC WireGuard VPS Node Topology for NEXORA VPN
-- Nodes stay in 'pending_vps' until real VPS endpoint & WireGuard public key are registered and verified online.

INSERT INTO vpn_servers (node_code, name, country_name, country_code, city, ipv4_subnet, ipv6_subnet, tier, health_status)
VALUES
  ('SA01', 'Riyadh SA01', 'Saudi Arabia', 'SA', 'Riyadh', '10.8.1.0/24', 'fd80:4e58:5201::/64', 'free', 'pending_vps'),
  ('SA02', 'Jeddah SA02', 'Saudi Arabia', 'SA', 'Jeddah', '10.8.2.0/24', 'fd80:4e58:5202::/64', 'pro', 'pending_vps'),
  ('AE01', 'Dubai AE01', 'United Arab Emirates', 'AE', 'Dubai', '10.8.3.0/24', 'fd80:4e58:5203::/64', 'free', 'pending_vps'),
  ('AE02', 'Abu Dhabi AE02', 'United Arab Emirates', 'AE', 'Abu Dhabi', '10.8.4.0/24', 'fd80:4e58:5204::/64', 'pro', 'pending_vps'),
  ('BH01', 'Manama BH01', 'Bahrain', 'BH', 'Manama', '10.8.5.0/24', 'fd80:4e58:5205::/64', 'free', 'pending_vps'),
  ('QA01', 'Doha QA01', 'Qatar', 'QA', 'Doha', '10.8.6.0/24', 'fd80:4e58:5206::/64', 'pro', 'pending_vps'),
  ('KW01', 'Kuwait City KW01', 'Kuwait', 'KW', 'Kuwait City', '10.8.7.0/24', 'fd80:4e58:5207::/64', 'pro', 'pending_vps'),
  ('OM01', 'Muscat OM01', 'Oman', 'OM', 'Muscat', '10.8.8.0/24', 'fd80:4e58:5208::/64', 'free', 'pending_vps')
ON CONFLICT (node_code) DO NOTHING;
