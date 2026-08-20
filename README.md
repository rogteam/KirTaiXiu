# KirTaiXiu

Plugin Tài Xỉu (Sic Bo) cho server Minecraft Paper 1.21. Người chơi đặt cược tiền ảo thông qua Vault vào kết quả của 3 viên xúc xắc. Vòng chơi tự động chạy liên tục, không cần thao tác của admin.

## Yêu cầu

| Thành phần | Phiên bản |
|---|---|
| Paper / Purpur | 1.21.x |
| Java | 21+ |
| Vault | 1.7+ |
| Economy plugin | EssentialsX, CMI, v.v. |

## Cài đặt

1. Build JAR: `./gradlew build` → lấy file trong `build/libs/KirTaiXiu-1.0.2.jar`
2. Đặt JAR vào thư mục `plugins/` của server
3. Đảm bảo Vault và economy plugin đã được cài
4. Khởi động server — plugin tự generate `config.yml` và `data.yml`

## Lệnh

Lệnh chính: `/taixiu` — bí danh: `/tx`

### Người chơi (`kirtaixiu.use` — mặc định: tất cả)

| Lệnh | Mô tả |
|---|---|
| `/tx` | Mở GUI thông tin cược |
| `/tx tai <tiền>` | Cược Tài (tổng 11–18) |
| `/tx xiu <tiền>` | Cược Xỉu (tổng 3–10) |
| `/tx chan <tiền>` | Cược Chẵn |
| `/tx le <tiền>` | Cược Lẻ |
| `/tx tong <3-18> <tiền>` | Cược đúng tổng điểm |
| `/tx tamhoa any <tiền>` | Cược Tam hoa bất kỳ |
| `/tx tamhoa <1-6> <tiền>` | Cược Tam hoa chính xác |
| `/tx jackpot` | Xem jackpot hiện tại |
| `/tx history` | Mở GUI lịch sử ván đấu |
| `/tx top` | Mở GUI bảng xếp hạng lợi nhuận |

> **Cú pháp tiền:** hỗ trợ hậu tố `k` (×1.000), `m` (×1.000.000), `b` (×1.000.000.000) và dấu phẩy. Ví dụ: `100k`, `1.5m`, `1,000,000`.

### Admin (`kirtaixiu.admin` — mặc định: OP)

| Lệnh | Mô tả |
|---|---|
| `/tx reload` | Reload `config.yml` |
| `/tx debug` | Kiểm tra cấu hình đang chạy |
| `/tx reset stats` | Xóa toàn bộ thống kê người chơi |
| `/tx reset history` | Xóa toàn bộ lịch sử ván đấu |
| `/tx reset jackpot` | Reset jackpot về giá trị seed |
| `/tx reset all` | Reset toàn bộ dữ liệu |

## Quyền hạn

| Permission | Mặc định | Mô tả |
|---|---|---|
| `kirtaixiu.use` | `true` | Tham gia đặt cược |
| `kirtaixiu.admin` | `op` | Reload, debug, reset dữ liệu |

## Tỉ lệ thưởng mặc định

| Loại cược | Điều kiện | Tỉ lệ |
|---|---|---|
| Tài / Xỉu | Tổng ≥ 11 / ≤ 10 | 1.9× |
| Chẵn / Lẻ | Tổng chẵn/lẻ | 1.9× |
| Tổng điểm | Đúng tổng (3–18) | 10× |
| Tam hoa bất kỳ | 3 xúc xắc bằng nhau | 28× |
| Tam hoa chính xác | 3 xúc xắc đúng số | 120× |

> Tỉ lệ là **bội số hoàn trả** (bao gồm tiền gốc). Phí nhà 3% (mặc định) được khấu trừ khi đặt cược, 50% phí này được nạp vào quỹ Jackpot.

## Jackpot (Nổ Hũ)

Jackpot được tích lũy từ phí nhà mỗi ván. Điều kiện trúng:
- Kết quả ra tam hoa với số nằm trong `jackpot.trigger-triples` (mặc định: 1 và 6)
- Người chơi phải đã cược **Tam hoa chính xác** đúng số đó
- Nếu nhiều người cùng trúng, jackpot chia đều

## Cấu hình nhanh (`config.yml`)

```yaml
general:
  round-seconds: 180        # Thời gian mỗi ván (giây)
  result-display-seconds: 8 # Thời gian hiển thị kết quả
  min-bet: 1000             # Cược tối thiểu
  max-bet: 10000000         # Cược tối đa

economy:
  house-fee-percent: 3.0           # % phí nhà
  jackpot-share-of-fee-percent: 50 # % phí nạp vào jackpot
  jackpot-seed: 1000000            # Jackpot ban đầu / sau reset

season:
  name: 'Mùa 1'
  leaderboard-size: 10
```

## Dữ liệu lưu trữ

File `plugins/KirTaiXiu/data.yml` lưu:
- Giá trị jackpot hiện tại
- Lịch sử ván đấu (tối đa `history.keep-last` entries)
- Thống kê từng người chơi (UUID, tên, wagered, profit, wins, losses)

Dữ liệu được lưu sau mỗi ván kết thúc và khi server shutdown.
