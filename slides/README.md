# Bo Slide Thuyet Trinh Do An Spring Batch

Tai lieu huong dan cach tao lai bo slide thuyet trinh, thong tin font chu va cac cong cu da su dung.

## 1. San pham ban giao

- `slides/snapgram-spring-batch.pptx`: File thuyet trinh PowerPoint goc (16:9, gom 16 slide, chu van ban hoan toan chinh sua duoc, co kem speaker notes tieng Viet cho tung slide).
- `slides/snapgram-spring-batch.pdf`: Ban xuat dinh dang PDF tu file pptx bang LibreOffice Kit, da nhung san toan bo font chu.
- `slides/preview/slide-01.png` den `slide-16.png`: Bo 16 anh ket xuat de kiem tra bo cuc va trinh chieu.
- `slides/fonts/`: Thu muc chua cac tap tin font goc (.ttf) giay phep mo.
- `slides/assets/icons/`: Bo icon pixel art 16x16 tu ve bang Pillow va bang tong hop sprite sheet.

## 2. Thong tin font chu va cach cai dat

Bo slide su dung phong cach Vintage, Retro, 8-bit giao dien cua so co:
- Tieu de slide: Font pixel VT323 (Google Fonts, Open Font License). Da duoc kiem tra dam bao hien thi day du 100% dau tieng Viet (a, an, o, u, d va cac dau thanh).
- Noi dung va ma nguon: Font Consolas (monospace chuan co san tren he dieu hanh Windows) hoac Space Mono.

### Cach cai font tren Windows:
1. Mo thu muc `slides/fonts/`.
2. Nhan dup chuot vao file `VT323-Regular.ttf`.
3. Bam nut "Install" (hoac "Install for all users").
4. Lam tuong tu voi cac file font `SpaceMono-Regular.ttf` neu may chua co.

### Cach cai font tren Linux (Ubuntu, Debian):
1. Sao chep file .ttf vao thu muc font he thong:
   `mkdir -p ~/.local/share/fonts`
   `cp slides/fonts/*.ttf ~/.local/share/fonts/`
2. Cap nhat bo dem font:
   `fc-cache -f -v`

## 3. Cach tao lai bo slide tu ma nguon

Quy trinh tu dong hoa bang Python va LibreOffice:

### Buoc 1: Tao bo icon pixel art
Chay lenh:
`python tools/generate_icons.py`
Lenh nay se doc luoi ma tran 16x16, ap dung bang mau co dinh (Navy, Mint, Vang, San ho, Xanh troi, Kem) va xuat ra 20 icon PNG phong to 16 lan cung sprite sheet kiem tra.

### Buoc 2: Tao file trinh chieu PPTX
Chay lenh:
`python tools/build_all_slides.py`
Lenh se tao ra file `slides/snapgram-spring-batch.pptx` gom 16 slide voi day du bo cuc cua so co, vien navy 4px, bong do cung 8px, tien do pixel o day slide va speaker notes tieng Viet (40 den 80 tu moi slide).

### Buoc 3: Xuat file PDF va ket xuat anh PNG kiem tra
Su dung LibreOffice Kit CLI:
`node <libreoffice-kit-cli> convert --input slides/snapgram-spring-batch.pptx --output slides/snapgram-spring-batch.pdf`
`node <libreoffice-kit-cli> render --input slides/snapgram-spring-batch.pptx --output-dir slides/preview_temp --pages "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16"`

### Buoc 4: Chay kiem tra tu dong
Chay lenh:
`python tools/validate_slides.py`
Kiem tra tu dong so luong slide (dung 16 slide), do dai notes (40 den 80 tu), khong chua ky tu cam (gach ngang dai, emoji), cac slide 1 den 12 khong chua tu khoa du an, va xac nhan PDF da nhung du font.

## 4. Luu y quan trong khi trinh chieu

- Khi trinh chieu tai giang duong, neu may tinh trinh chieu chua cai font VT323, file PowerPoint (.pptx) co the bi thay the bang font chu khac lam sai lech bo cuc pixel.
- Giai phap an toan nhat: Su dung file `slides/snapgram-spring-batch.pdf` da nhung san 100% font chu de trinh chieu o che do Full Screen tren bat ky thiet bi nao.
