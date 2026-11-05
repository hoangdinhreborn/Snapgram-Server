import os
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
from pptx.enum.shapes import MSO_SHAPE

# 16:9 Presentation Dimensions
SLIDE_W = Inches(13.333)
SLIDE_H = Inches(7.5)

# Fixed Retro Vibrant Nostalgia Palette
COLOR_CREAM = RGBColor(0xFF, 0xF8, 0xE7)  # #FFF8E7 - Slide Background
COLOR_NAVY  = RGBColor(0x1B, 0x2A, 0x49)  # #1B2A49 - Text, Borders, Shadows
COLOR_MINT  = RGBColor(0x7B, 0xD8, 0xB0)  # #7BD8B0 - Part B
COLOR_YELLOW= RGBColor(0xFF, 0xD2, 0x5A)  # #FFD25A - Part A
COLOR_CORAL = RGBColor(0xFF, 0x7A, 0x66)  # #FF7A66 - Accent
COLOR_SKY   = RGBColor(0x8E, 0xC9, 0xF2)  # #8EC9F2 - Part C
COLOR_WHITE = RGBColor(0xFF, 0xFF, 0xFF)  # #FFFFFF - Sub-card background

FONT_TITLE = "VT323"
FONT_BODY  = "Consolas"
FONT_CODE  = "Consolas"

def create_presentation():
    prs = Presentation()
    prs.slide_width = SLIDE_W
    prs.slide_height = SLIDE_H
    return prs

def set_slide_background(slide):
    # Add a full-bleed cream background rectangle
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, SLIDE_W, SLIDE_H)
    bg.fill.solid()
    bg.fill.fore_color.rgb = COLOR_CREAM
    bg.line.fill.background()
    return bg

def draw_retro_window(slide, left, top, width, height, title_text, section="A"):
    # Section header color
    if section == "A":
        header_color = COLOR_YELLOW
    elif section == "B":
        header_color = COLOR_MINT
    else:
        header_color = COLOR_SKY
    
    shadow_offset = Inches(0.08)  # ~8px hard shadow
    
    # 1. Hard Shadow (Navy rectangle behind window)
    shadow = slide.shapes.add_shape(
        MSO_SHAPE.RECTANGLE,
        left + shadow_offset, top + shadow_offset,
        width, height
    )
    shadow.fill.solid()
    shadow.fill.fore_color.rgb = COLOR_NAVY
    shadow.line.fill.background()
    
    # 2. Window Main Body (White fill, thick Navy border)
    body = slide.shapes.add_shape(
        MSO_SHAPE.RECTANGLE,
        left, top, width, height
    )
    body.fill.solid()
    body.fill.fore_color.rgb = COLOR_WHITE
    body.line.color.rgb = COLOR_NAVY
    body.line.width = Pt(3.5)
    
    # 3. Window Title Bar (Header color fill, navy border)
    title_bar_h = Inches(0.95)
    tbar = slide.shapes.add_shape(
        MSO_SHAPE.RECTANGLE,
        left, top, width, title_bar_h
    )
    tbar.fill.solid()
    tbar.fill.fore_color.rgb = header_color
    tbar.line.color.rgb = COLOR_NAVY
    tbar.line.width = Pt(3.5)
    
    # 4. Three 8-bit window buttons on top right
    btn_size = Inches(0.30)
    btn_y = top + Inches(0.32)
    btn_colors = [COLOR_YELLOW, COLOR_MINT, COLOR_CORAL]
    for i, c in enumerate(btn_colors):
        bx = left + width - Inches(1.30) + Inches(i * 0.38)
        btn = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, bx, btn_y, btn_size, btn_size)
        btn.fill.solid()
        btn.fill.fore_color.rgb = c
        btn.line.color.rgb = COLOR_NAVY
        btn.line.width = Pt(2)
    
    # 5. Title Text
    # Calculate title font size (40pt default, adjust if long)
    title_font_size = Pt(40) if len(title_text) <= 45 else Pt(34)
    tb = slide.shapes.add_textbox(left + Inches(0.3), top + Inches(0.04), width - Inches(1.7), title_bar_h)
    tf = tb.text_frame
    tf.word_wrap = True
    tf.margin_left = tf.margin_top = tf.margin_right = tf.margin_bottom = 0
    p = tf.paragraphs[0]
    p.alignment = PP_ALIGN.LEFT
    r = p.add_run()
    r.text = title_text
    r.font.name = FONT_TITLE
    r.font.size = title_font_size
    r.font.bold = True
    r.font.color.rgb = COLOR_NAVY
    
    # Return inner client area bounds
    inner_top = top + title_bar_h + Inches(0.15)
    inner_h = height - title_bar_h - Inches(0.3)
    inner_left = left + Inches(0.4)
    inner_w = width - Inches(0.8)
    return (inner_left, inner_top, inner_w, inner_h)

def draw_footer_and_progress(slide, current_slide, total_slides=16):
    # Bottom pixel progress bar: 16 squares
    box_w = Inches(0.32)
    box_h = Inches(0.16)
    spacing = Inches(0.08)
    start_x = Inches(0.6)
    start_y = Inches(7.05)
    
    for i in range(total_slides):
        bx = start_x + Inches(i * (0.32 + 0.08))
        sq = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, bx, start_y, box_w, box_h)
        sq.line.color.rgb = COLOR_NAVY
        sq.line.width = Pt(1.5)
        if (i + 1) <= current_slide:
            sq.fill.solid()
            # Color based on section
            if current_slide <= 3:
                sq.fill.fore_color.rgb = COLOR_YELLOW
            elif current_slide <= 12:
                sq.fill.fore_color.rgb = COLOR_MINT
            else:
                sq.fill.fore_color.rgb = COLOR_SKY
        else:
            sq.fill.solid()
            sq.fill.fore_color.rgb = COLOR_WHITE
            
    # Slide Number e.g. "03/16"
    num_box = slide.shapes.add_textbox(Inches(11.2), Inches(6.92), Inches(1.5), Inches(0.4))
    ntf = num_box.text_frame
    ntf.word_wrap = False
    ntf.margin_left = ntf.margin_top = ntf.margin_right = ntf.margin_bottom = 0
    p = ntf.paragraphs[0]
    p.alignment = PP_ALIGN.RIGHT
    r = p.add_run()
    r.text = f"{current_slide:02d}/{total_slides:02d}"
    r.font.name = FONT_TITLE
    r.font.size = Pt(28)
    r.font.bold = True
    r.font.color.rgb = COLOR_NAVY

def draw_card(slide, left, top, width, height, bg_color=COLOR_WHITE):
    # Hard shadow
    shadow_offset = Inches(0.06)
    sh = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, left + shadow_offset, top + shadow_offset, width, height)
    sh.fill.solid()
    sh.fill.fore_color.rgb = COLOR_NAVY
    sh.line.fill.background()
    
    # Main card
    card = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, left, top, width, height)
    card.fill.solid()
    card.fill.fore_color.rgb = bg_color
    card.line.color.rgb = COLOR_NAVY
    card.line.width = Pt(2.5)
    return card

def add_notes(slide, text):
    notes_slide = slide.notes_slide
    tf = notes_slide.notes_text_frame
    tf.text = text

def build_prototype():
    prs = create_presentation()
    os.makedirs("slides/test", exist_ok=True)
    
    # -------------------------------------------------------------
    # SLIDE 1 (Sample Part A)
    # -------------------------------------------------------------
    s1 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s1)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s1, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Spring Batch: xử lý hàng triệu bản ghi an toàn",
        section="A"
    )
    draw_footer_and_progress(s1, 1, 16)
    
    # Subtitle
    tb_sub = s1.shapes.add_textbox(c_left, c_top, c_w, Inches(0.5))
    tf_sub = tb_sub.text_frame
    tf_sub.word_wrap = True
    p = tf_sub.paragraphs[0]
    r = p.add_run()
    r.text = "Cách hoạt động và khi nào nên dùng trong thực tế"
    r.font.name = FONT_BODY
    r.font.size = Pt(20)
    r.font.bold = True
    r.font.color.rgb = COLOR_NAVY
    
    # Left: Metadata card (Tên nhóm, môn học, giảng viên)
    draw_card(s1, c_left, c_top + Inches(0.7), Inches(5.3), Inches(4.0), COLOR_CREAM)
    meta_box = s1.shapes.add_textbox(c_left + Inches(0.3), c_top + Inches(0.9), Inches(4.8), Inches(3.6))
    mtf = meta_box.text_frame
    mtf.word_wrap = True
    
    lines = [
        ("Môn học:", "Kiến trúc Phần mềm"),
        ("Đồ án:", "Tìm hiểu Spring Batch"),
        ("Giảng viên:", "[Điền tên giảng viên hướng dẫn]"),
        ("Nhóm SV:", "[Điền tên các thành viên nhóm]"),
        ("Thời gian:", "Học kỳ 1 (2024 - 2025)"),
    ]
    for idx, (label, val) in enumerate(lines):
        p = mtf.paragraphs[0] if idx == 0 else mtf.add_paragraph()
        p.space_after = Pt(8)
        r1 = p.add_run()
        r1.text = f"{label} "
        r1.font.name = FONT_BODY
        r1.font.size = Pt(18)
        r1.font.bold = True
        r1.font.color.rgb = COLOR_NAVY
        r2 = p.add_run()
        r2.text = val
        r2.font.name = FONT_BODY
        r2.font.size = Pt(18)
        r2.font.color.rgb = COLOR_NAVY
        
    # Right: Big Retro Illustration Card (Conveyor, Chunk boxes, Gear)
    draw_card(s1, c_left + Inches(5.6), c_top + Inches(0.7), Inches(5.7), Inches(4.0), COLOR_WHITE)
    # Add conveyor and boxes icons
    s1.shapes.add_picture("slides/assets/icons/conveyor.png", c_left + Inches(6.0), c_top + Inches(1.1), Inches(2.2), Inches(2.2))
    s1.shapes.add_picture("slides/assets/icons/chunk_box.png", c_left + Inches(8.5), c_top + Inches(1.1), Inches(2.2), Inches(2.2))
    
    # Caption below icons
    cap_box = s1.shapes.add_textbox(c_left + Inches(5.8), c_top + Inches(3.5), Inches(5.3), Inches(0.8))
    cp = cap_box.text_frame.paragraphs[0]
    cp.alignment = PP_ALIGN.CENTER
    cr = cp.add_run()
    cr.text = "Dây chuyền xử lý dữ liệu hàng loạt công nghiệp"
    cr.font.name = FONT_TITLE
    cr.font.size = Pt(22)
    cr.font.color.rgb = COLOR_NAVY
    
    add_notes(s1, "Xin chào thầy cô và các bạn. Hôm nay nhóm em xin trình bày về Spring Batch, một framework chuyên dụng để xử lý khối lượng lớn dữ liệu an toàn và tin cậy. Chúng ta sẽ cùng tìm hiểu cách Spring Batch vận hành và khi nào nên áp dụng. Hãy bắt đầu với một bài toán thực tế rất gần gũi.")
    
    # -------------------------------------------------------------
    # SLIDE 7 (Sample Part B)
    # -------------------------------------------------------------
    s7 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s7)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s7, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Chunk: chấm từng xấp bài, không ôm cả núi bài",
        section="B"
    )
    draw_footer_and_progress(s7, 7, 16)
    
    # Analogy explanation card
    draw_card(s7, c_left, c_top, Inches(11.3), Inches(1.2), COLOR_CREAM)
    s7.shapes.add_picture("slides/assets/icons/paper_stack.png", c_left + Inches(0.25), c_top + Inches(0.12), Inches(0.95), Inches(0.95))
    abox = s7.shapes.add_textbox(c_left + Inches(1.35), c_top + Inches(0.12), Inches(9.7), Inches(0.95))
    ap = abox.text_frame.paragraphs[0]
    ar = ap.add_run()
    ar.text = "Ví dụ chấm thi: cô giáo chia 500 bài thi thành từng xấp 20 bài. Chấm xong mỗi xấp thì ghi điểm ngay vào sổ. Nếu có sự cố, cô chỉ cần chấm lại xấp đang dở, không phải chấm lại từ đầu."
    ar.font.name = FONT_BODY
    ar.font.size = Pt(17)
    ar.font.color.rgb = COLOR_NAVY
    
    # Diagram 3 blocks: Reader -> Processor -> Writer -> Commit
    diag_top = c_top + Inches(1.4)
    draw_card(s7, c_left, diag_top, Inches(5.5), Inches(3.6), COLOR_WHITE)
    dbox = s7.shapes.add_textbox(c_left + Inches(0.3), diag_top + Inches(0.15), Inches(4.9), Inches(3.3))
    dtf = dbox.text_frame
    dtf.word_wrap = True
    
    p = dtf.paragraphs[0]
    p.space_after = Pt(6)
    r = p.add_run()
    r.text = "Cơ chế xử lý theo Chunk (N bản ghi):"
    r.font.name = FONT_TITLE
    r.font.size = Pt(24)
    r.font.bold = True
    r.font.color.rgb = COLOR_NAVY
    
    bullets = [
        "Reader đọc từng bản ghi cho đủ N mục",
        "Processor nhận từng mục và xử lý",
        "Writer nhận trọn vẹn N mục đã xử lý",
        "Commit một giao dịch duy nhất cho cả xấp"
    ]
    for b in bullets:
        bp = dtf.add_paragraph()
        bp.space_after = Pt(5)
        br = bp.add_run()
        br.text = f"* {b}"
        br.font.name = FONT_BODY
        br.font.size = Pt(17)
        br.font.color.rgb = COLOR_NAVY
        
    # Right: Tradeoff card
    draw_card(s7, c_left + Inches(5.8), diag_top, Inches(5.5), Inches(3.6), COLOR_WHITE)
    tbox = s7.shapes.add_textbox(c_left + Inches(6.1), diag_top + Inches(0.15), Inches(4.9), Inches(3.3))
    ttf = tbox.text_frame
    ttf.word_wrap = True
    
    p = ttf.paragraphs[0]
    p.space_after = Pt(6)
    r = p.add_run()
    r.text = "Lựa chọn kích thước Chunk phù hợp:"
    r.font.name = FONT_TITLE
    r.font.size = Pt(24)
    r.font.bold = True
    r.font.color.rgb = COLOR_NAVY
    
    tradeoffs = [
        "Xấp quá nhỏ: lưu liên tục tốn công mở giao dịch",
        "Xấp quá lớn: tốn bộ nhớ RAM, nếu lỗi phải làm lại nhiều",
        "Thực tế: thường chọn từ 20 đến 500 bản ghi",
        "Mỗi xấp hoàn tất là một mốc an toàn được lưu chắc"
    ]
    for t in tradeoffs:
        tp = ttf.add_paragraph()
        tp.space_after = Pt(5)
        tr = tp.add_run()
        tr.text = f"* {t}"
        tr.font.name = FONT_BODY
        tr.font.size = Pt(17)
        tr.font.color.rgb = COLOR_NAVY
        
    add_notes(s7, "Thay vì ôm trọn cả núi 5 triệu tài khoản, Spring Batch chia nhỏ thành từng xấp gọi là chunk. Giống như cô giáo chấm từng xấp 20 bài thi rồi ghi điểm vào sổ một lần. Cách này giúp bộ nhớ máy luôn nhẹ và mỗi xấp commit là một bước tiến chắc chắn. Vậy hệ thống làm sao nhớ đã chấm đến xấp nào, chúng ta cùng xem tiếp.")

    # -------------------------------------------------------------
    # SLIDE 15 (Sample Part C)
    # -------------------------------------------------------------
    s15 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s15)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s15, Inches(0.6), Inches(0.5), Inches(12.133), Inches(6.3),
        "Demo: 5 bước trên bản đồ màn chơi",
        section="C"
    )
    draw_footer_and_progress(s15, 15, 16)
    
    # 5 Game-stage cards arranged across the slide
    steps_data = [
        ("MÀN 1", "seed_data", "Nạp 5.000 bản ghi", "Cài 3 bản ghi lỗi cố ý", "warning_triangle", "Minh họa Skip"),
        ("MÀN 2", "run_job", "Khởi chạy Job", "Xem log commit từng xấp 20", "conveyor", "Minh họa Chunk"),
        ("MÀN 3", "metrics", "Kiểm tra số liệu", "Xem read, write, skip, commit", "notebook", "Minh họa Metadata"),
        ("MÀN 4", "cache", "Người dùng đọc cache", "Lấy Top 50 siêu tốc từ Redis", "coin", "Minh họa Cache-First"),
        ("MÀN 5", "restart", "Phục hồi sự cố", "Lỗi item 45, restart chạy tiếp", "restart_arrow", "Minh họa Restart"),
    ]
    
    card_w = Inches(2.14)
    card_h = Inches(4.3)
    card_gap = Inches(0.15)
    
    for i, (stage, code_name, title, desc, icon_name, concept) in enumerate(steps_data):
        cx = c_left + Inches(i * (2.14 + 0.15))
        cy = c_top + Inches(0.1)
        
        # Draw card
        card_bg = COLOR_WHITE if i % 2 == 0 else COLOR_CREAM
        draw_card(s15, cx, cy, card_w, card_h, card_bg)
        
        # Header banner inside card
        sb = s15.shapes.add_shape(MSO_SHAPE.RECTANGLE, cx, cy, card_w, Inches(0.5))
        sb.fill.solid()
        sb.fill.fore_color.rgb = COLOR_SKY if i == 4 else (COLOR_YELLOW if i == 0 else COLOR_MINT)
        sb.line.color.rgb = COLOR_NAVY
        sb.line.width = Pt(2)
        
        sp = sb.text_frame.paragraphs[0]
        sp.alignment = PP_ALIGN.CENTER
        sr = sp.add_run()
        sr.text = stage
        sr.font.name = FONT_TITLE
        sr.font.size = Pt(22)
        sr.font.bold = True
        sr.font.color.rgb = COLOR_NAVY
        
        # Icon
        s15.shapes.add_picture(f"slides/assets/icons/{icon_name}.png", cx + Inches(0.57), cy + Inches(0.65), Inches(1.0), Inches(1.0))
        
        # Text details
        sbox = s15.shapes.add_textbox(cx + Inches(0.1), cy + Inches(1.75), card_w - Inches(0.2), Inches(2.4))
        stf = sbox.text_frame
        stf.word_wrap = True
        stf.margin_left = stf.margin_right = stf.margin_top = stf.margin_bottom = 0
        
        p1 = stf.paragraphs[0]
        p1.alignment = PP_ALIGN.CENTER
        p1.space_after = Pt(4)
        r1 = p1.add_run()
        r1.text = title
        r1.font.name = FONT_BODY
        r1.font.size = Pt(17)
        r1.font.bold = True
        r1.font.color.rgb = COLOR_NAVY
        
        p2 = stf.add_paragraph()
        p2.alignment = PP_ALIGN.CENTER
        p2.space_after = Pt(8)
        r2 = p2.add_run()
        r2.text = desc
        r2.font.name = FONT_BODY
        r2.font.size = Pt(15)
        r2.font.color.rgb = COLOR_NAVY
        
        # Concept badge at bottom
        p3 = stf.add_paragraph()
        p3.alignment = PP_ALIGN.CENTER
        r3 = p3.add_run()
        r3.text = f"[{concept}]"
        r3.font.name = FONT_TITLE
        r3.font.size = Pt(18)
        r3.font.bold = True
        r3.font.color.rgb = COLOR_CORAL
        
    add_notes(s15, "Để chứng minh thực tế, nhóm đã chuẩn bị kịch bản demo gồm 5 bước như 5 màn chơi. Từ bước nạp dữ liệu có cài sẵn bản ghi lỗi, kích hoạt job để thấy xử lý từng xấp, kiểm tra số liệu lưu trong sổ, đọc cache Redis, cho đến bước giả lập sự cố ở bản ghi 45 rồi restart thành công. Giờ chúng ta hãy xem kết quả thu được.")

    prs.save("slides/test/prototype.pptx")
    print("Saved prototype.pptx successfully!")

if __name__ == "__main__":
    build_prototype()
