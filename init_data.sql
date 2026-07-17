-- ============================================
-- 智慧交通监控预警系统 - 初始化数据脚本
-- 10条用户数据 + 10条设备信息
-- ============================================

USE traffic_warning_system;

-- ============================================
-- 清理旧数据（保留管理员账号 admin）
-- ============================================
DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username != 'admin');
DELETE FROM sys_user WHERE username != 'admin';
DELETE FROM traffic_device;

-- ============================================
-- 插入 10 条用户数据
-- ============================================

-- 用户1: admin (已存在则更新)
INSERT INTO sys_user (username, password, display_name, phone, email, role, status) VALUES
('admin', 'admin123', '系统管理员', '13800000001', 'admin@traffic.com', '管理员', '正常')
ON DUPLICATE KEY UPDATE display_name='系统管理员', phone='13800000001', email='admin@traffic.com', role='管理员', status='正常';

-- 用户2: zhangwei
INSERT INTO sys_user (username, password, display_name, phone, email, role, status) VALUES
('zhangwei', '123456', '张伟', '13800000002', 'zhangwei@traffic.com', '普通用户', '正常');

-- 用户3: liming
INSERT INTO sys_user (username, password, display_name, phone, email, role, status) VALUES
('liming', '123456', '李明', '13800000003', 'liming@traffic.com', '普通用户', '正常');

-- 用户4: wangfang
INSERT INTO sys_user (username, password, display_name, phone, email, role, status) VALUES
('wangfang', '123456', '王芳', '13800000004', 'wangfang@traffic.com', '普通用户', '正常');

-- 用户5: chenjie
INSERT INTO sys_user (username, password, display_name, phone, email, role, status) VALUES
('chenjie', '123456', '陈杰', '13800000005', 'chenjie@traffic.com', '普通用户', '正常');

-- 用户6: liuna
INSERT INTO sys_user (username, password, display_name, phone, email, role, status) VALUES
('liuna', '123456', '刘娜', '13800000006', 'liuna@traffic.com', '普通用户', '正常');

-- 用户7: zhaoyang
INSERT INTO sys_user (username, password, display_name, phone, email, role, status) VALUES
('zhaoyang', '123456', '赵阳', '13800000007', 'zhaoyang@traffic.com', '普通用户', '正常');

-- 用户8: huangli (管理员)
INSERT INTO sys_user (username, password, display_name, phone, email, role, status) VALUES
('huangli', '123456', '黄丽', '13800000008', 'huangli@traffic.com', '管理员', '正常');

-- 用户9: zhoujie (禁用状态)
INSERT INTO sys_user (username, password, display_name, phone, email, role, status) VALUES
('zhoujie', '123456', '周杰', '13800000009', 'zhoujie@traffic.com', '普通用户', '禁用');

-- 用户10: suntao
INSERT INTO sys_user (username, password, display_name, phone, email, role, status) VALUES
('suntao', '123456', '孙涛', '13800000010', 'suntao@traffic.com', '普通用户', '正常');

-- ============================================
-- 为用户分配角色 (sys_user_role)
-- ============================================
-- 先清理旧的角色分配
DELETE FROM sys_user_role;

-- admin -> 管理员 (role_id=1)
INSERT INTO sys_user_role (user_id, role_id)
SELECT id, 1 FROM sys_user WHERE username = 'admin';

-- huangli -> 管理员 (role_id=1)
INSERT INTO sys_user_role (user_id, role_id)
SELECT id, 1 FROM sys_user WHERE username = 'huangli';

-- 其余用户 -> 普通用户 (role_id=2)
INSERT INTO sys_user_role (user_id, role_id)
SELECT id, 2 FROM sys_user WHERE username NOT IN ('admin', 'huangli');

-- ============================================
-- 补充道路数据（确保 road_id 1~10 存在）
-- ============================================
INSERT IGNORE INTO traffic_road (id, road_code, road_name, location, direction, status) VALUES
(1, 'R001', '城市隧道入口', '城市地铁隧道入口处', '入口', '正常'),
(2, 'R002', '城市主干道与高架桥交叉口', '城市主干道与辅路/高架桥交叉口', '双向', '正常'),
(3, 'R003', '高架公路入口匝道', '高架公路入口匝道', '出城方向', '正常'),
(4, 'R004', '长江大桥北引桥', '南京长江大桥北引桥段', '双向', '正常'),
(5, 'R005', '新街口商圈环岛', '南京市秦淮区新街口环岛', '多向', '正常'),
(6, 'R006', '龙蟠中路高架', '南京市秦淮区龙蟠中路', '南北向', '正常'),
(7, 'R007', '应天大街快速路', '南京市建邺区应天大街', '东西向', '正常'),
(8, 'R008', '南京南站北广场', '南京市雨花台区南京南站', '多向', '正常'),
(9, 'R009', '绕城公路马群枢纽', '南京市栖霞区马群枢纽', '双向', '正常'),
(10, 'R010', '江北大道快速路', '南京市浦口区江北大道', '南北向', '正常');

-- ============================================
-- 插入 10 条设备信息
-- ============================================

INSERT INTO traffic_device (id, name, road_id, location, rtsp, creator, status) VALUES
('C001', '中山路主干道监控', 1, '南京市鼓楼区中山路与解放路交叉口', 'rtsp://192.168.1.101:554/stream1', 'admin', '在线'),
('C002', '沪宁高速南京段监控', 2, '沪宁高速南京收费站往上海方向3公里处', 'rtsp://192.168.1.102:554/stream1', 'admin', '在线'),
('C003', '玄武大道隧道监控', 3, '南京市玄武区玄武大道隧道入口', 'rtsp://192.168.1.103:554/stream1', 'admin', '在线'),
('C004', '长江大桥北端监控', 4, '南京长江大桥北引桥200米处', 'rtsp://192.168.1.104:554/stream1', 'admin', '在线'),
('C005', '新街口商圈监控', 5, '南京市秦淮区新街口十字路口', 'rtsp://192.168.1.105:554/stream1', 'huangli', '在线'),
('C006', '龙蟠中路高架监控', 6, '南京市秦淮区龙蟠中路高架段', 'rtsp://192.168.1.106:554/stream1', 'huangli', '离线'),
('C007', '应天大街快速路监控', 7, '南京市建邺区应天大街快速路', 'rtsp://192.168.1.107:554/stream1', 'admin', '在线'),
('C008', '南京南站周边监控', 8, '南京市雨花台区南京南站北广场', 'rtsp://192.168.1.108:554/stream1', 'admin', '离线'),
('C009', '绕城公路马群段监控', 9, '南京市栖霞区绕城公路马群枢纽', 'rtsp://192.168.1.109:554/stream1', 'zhangwei', '在线'),
('C010', '江北大道快速路监控', 10, '南京市浦口区江北大道快速路', 'rtsp://192.168.1.110:554/stream1', 'zhangwei', '在线');
