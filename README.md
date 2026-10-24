# Event Management System

一个 Java 17 命令行应用，用来管理活动、报名和出勤，数据保存在本地文件中。

本项目按 [Jira OPEN 项目](https://1951783639.atlassian.net/jira/software/projects/OPEN/boards/34/backlog) 在 **2026-10-24** 的实际完成度制作：Sprint 1 全部完成，Sprint 2 完成 4/7。代码只实现看板中 **Done** 的 9 个任务，未提前实现剩余 3 个 To Do，因此项目状态与 Jira 一致。

## Jira 进度对照

| Jira | 任务 | 故事点 | Jira 状态 | 本项目 |
| --- | --- | ---: | --- | --- |
| OPEN-1 | Create Event | 5 | Done | 菜单 1：创建活动，校验名称、未来时间、场地、容量，自动生成唯一 ID |
| OPEN-2 | List Events | 3 | Done | 菜单 2：列表、空列表显示 `No events`、按名称/ID/日期/场地/任意字段筛选 |
| OPEN-10 | Edit Event | 2 | Done | 菜单 4：编辑名称、时间、场地；已开始的活动拒绝编辑 |
| OPEN-8 | Cancel Event | 3 | Done | 菜单 5：取消/恢复活动，取消后归入 Cancelled 区域且停止报名 |
| OPEN-4 | Manage Event Capacity | 3 | Done | 菜单 6：更新容量，不得低于已报名人数，满员阻止新报名 |
| OPEN-3 | Register Attendee | 5 | Done | 菜单 7：报名、展示当前报名数、生成唯一报名编号、满员提示 |
| OPEN-5 | Track Attendance | 2 | Done | 菜单 9：标记出勤、展示报名/出勤实时人数、未报名者不能标记 |
| OPEN-6 | Cancel Registration | 2 | Done | 菜单 8：二次确认后取消报名，释放名额并从出勤列表移除 |
| OPEN-9 | Data Persistence | 5 | Done | 单文件持久化，重启后数据仍在；临时文件 + 原子替换 + fsync 防止半写损坏 |
| OPEN-7 | Generate Reports | 5 | **To Do** | 未实现：没有报表页面，也没有导出文件功能 |
| OPEN-11 | Add JUnit regression suite | 5 | **To Do** | 未实现：没有 JUnit 依赖、测试目录或 CI 配置 |
| OPEN-12 | Add file locking for concurrent access | 3 | **To Do** | 未实现：没有文件锁或乐观并发控制，只支持单进程使用 |

当前故事点：**30 / 43（约 69.8%）**；工作项：**9 / 12 完成**。

## 运行要求

- Java 17 或更高版本（本机使用 Java 25 构建，产物目标版本为 17）
- 项目自带 Maven Wrapper，无需预装 Maven；首次构建需要联网下载 Maven

## 构建与启动

macOS / Linux / Git Bash：

```bash
./mvnw clean package
java -jar target/event-management-system-1.0.0.jar
```

Windows CMD / PowerShell：

```bat
mvnw.cmd clean package
java -jar target\event-management-system-1.0.0.jar
```

如果 Windows 终端里的中文显示为乱码，先执行 `chcp 65001` 切换到 UTF-8。程序输出固定使用 UTF-8，数据文件同样使用 UTF-8。

## 数据文件

默认路径是运行目录下的 `data/events.db`，首次保存时自动创建。启动时会打印实际路径。可以覆盖：

```bash
java -Devent.data.file=/tmp/my-events.db -jar target/event-management-system-1.0.0.jar
```

```bat
java -Devent.data.file=C:\temp\my-events.db -jar target\event-management-system-1.0.0.jar
```

持久化策略：

- 所有活动、报名和出勤信息写入同一个文本文件
- 保存时先写同目录临时文件，`fsync` 后再原子替换正式文件，异常退出不会留下半写文件
- 启动时若文件格式损坏、记录重复或引用不存在，程序会拒绝启动并保留原文件，不会覆盖
- **未实现并发写保护**（对应 OPEN-12）：不要同时运行多个实例写同一个数据文件

## 使用说明

启动后使用数字菜单：

```text
1. Create event
2. List / search events
3. View event details
4. Edit event
5. Cancel / restore event
6. Update event capacity
7. Register attendee
8. Cancel registration
9. Mark attendance
0. Exit
```

日期时间格式为 `yyyy-MM-dd HH:mm`，也接受 ISO 格式（如 `2026-12-01T18:00`）。活动列表按开始时间排序，已取消的活动单独显示在 `Cancelled events` 区域。活动详情中包含容量、已报名人数、已出勤人数和报名明细；报名编号形如 `REG-9RTWCQ`。

## 项目结构

```text
src/main/java/com/eventmanagement/
├── Main.java                        # 入口、数据文件配置
├── cli/
│   ├── ConsoleApp.java              # 菜单、输入校验、业务流程编排
│   ├── ConsoleIO.java               # 输入输出和确认提示
│   └── TablePrinter.java            # 支持中文宽字符的表格输出
├── domain/
│   ├── Event.java                   # 活动
│   ├── EventStatus.java             # ACTIVE / CANCELLED
│   └── Registration.java            # 报名与出勤状态
├── persistence/
│   ├── DataSnapshot.java            # 一次性持久化的数据快照
│   ├── EventRepository.java
│   └── FileEventRepository.java     # 原子文件读写
└── service/
    ├── EventService.java            # 所有业务规则
    └── EventServiceException.java
```

业务规则集中在 `EventService`，CLI 只负责交互；持久化在每次写操作时先落盘成功、再更新内存，因此保存失败时不会出现“界面显示成功但数据未写入”的情况。

## 验证范围

已做手工端到端验证：

- 空列表显示 `No events`
- 创建活动的空字段、非法日期、过去日期、非正容量逐项报错并重新询问
- 唯一活动 ID 与唯一报名编号
- 列表、按名称/ID/日期/场地/任意字段筛选
- 编辑活动、已开始活动拒绝编辑、编辑时拒绝过去日期
- 取消/恢复活动、取消后的活动区块与报名拦截
- 容量下限校验、满员拦截、取消报名后释放名额
- 出勤标记、未报名编号拒绝、报名/出勤人数展示
- 重复运行后的数据持久化、中文活动名/报名人名的 UTF-8 往返
- 损坏数据文件拒绝启动且不覆盖原文件

JUnit 自动化测试和 CI 属于 OPEN-11，仍然保持未完成；因此本仓库没有 `src/test`。报表与文件锁同理，分别对应 OPEN-7 和 OPEN-12。
