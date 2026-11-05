import os
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
from pptx.enum.shapes import MSO_SHAPE

# 16:9 Slide Dimensions
SLIDE_W = Inches(13.333)
SLIDE_H = Inches(7.5)

# Fixed Retro Vibrant Nostalgia Palette
COLOR_CREAM = RGBColor(0xFF, 0xF8, 0xE7)  # #FFF8E7 - Background
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
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, SLIDE_W, SLIDE_H)
    bg.fill.solid()
    bg.fill.fore_color.rgb = COLOR_CREAM
    bg.line.fill.background()
    return bg

def draw_retro_window(slide, left, top, width, height, title_text, section="A"):
    if section == "A":
        header_color = COLOR_YELLOW
    elif section == "B":
        header_color = COLOR_MINT
    else:
        header_color = COLOR_SKY
    
    shadow_offset = Inches(0.08)
    
    # 1. Hard Shadow
    shadow = slide.shapes.add_shape(
        MSO_SHAPE.RECTANGLE,
        left + shadow_offset, top + shadow_offset,
        width, height
    )
    shadow.fill.solid()
    shadow.fill.fore_color.rgb = COLOR_NAVY
    shadow.line.fill.background()
    
    # 2. Window Main Body
    body = slide.shapes.add_shape(
        MSO_SHAPE.RECTANGLE,
        left, top, width, height
    )
    body.fill.solid()
    body.fill.fore_color.rgb = COLOR_WHITE
    body.line.color.rgb = COLOR_NAVY
    body.line.width = Pt(3.5)
    
    # 3. Window Title Bar
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
    
    # 5. Title Text (Min 40pt, auto-scaled if longer)
    if len(title_text) <= 42:
        title_font_size = Pt(40)
    elif len(title_text) <= 50:
        title_font_size = Pt(34)
    else:
        title_font_size = Pt(29)
    tb = slide.shapes.add_textbox(left + Inches(0.3), top + Inches(0.04), width - Inches(1.7), title_bar_h)
    tf = tb.text_frame
    tf.word_wrap = False
    tf.margin_left = tf.margin_top = tf.margin_right = tf.margin_bottom = 0
    p = tf.paragraphs[0]
    p.alignment = PP_ALIGN.LEFT
    r = p.add_run()
    r.text = title_text
    r.font.name = FONT_TITLE
    r.font.size = title_font_size
    r.font.bold = True
    r.font.color.rgb = COLOR_NAVY
    
    inner_top = top + title_bar_h + Inches(0.15)
    inner_h = height - title_bar_h - Inches(0.3)
    inner_left = left + Inches(0.4)
    inner_w = width - Inches(0.8)
    return (inner_left, inner_top, inner_w, inner_h)

def draw_footer_and_progress(slide, current_slide, total_slides=16):
    box_w = Inches(0.32)
    box_h = Inches(0.16)
    start_x = Inches(0.6)
    start_y = Inches(7.05)
    
    for i in range(total_slides):
        bx = start_x + Inches(i * (0.32 + 0.08))
        sq = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, bx, start_y, box_w, box_h)
        sq.line.color.rgb = COLOR_NAVY
        sq.line.width = Pt(1.5)
        if (i + 1) <= current_slide:
            sq.fill.solid()
            if current_slide <= 3:
                sq.fill.fore_color.rgb = COLOR_YELLOW
            elif current_slide <= 12:
                sq.fill.fore_color.rgb = COLOR_MINT
            else:
                sq.fill.fore_color.rgb = COLOR_SKY
        else:
            sq.fill.solid()
            sq.fill.fore_color.rgb = COLOR_WHITE
            
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
    shadow_offset = Inches(0.06)
    sh = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, left + shadow_offset, top + shadow_offset, width, height)
    sh.fill.solid()
    sh.fill.fore_color.rgb = COLOR_NAVY
    sh.line.fill.background()
    
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

def add_bullet_list(text_frame, items, font_size=Pt(17), space_after=Pt(5), title_text=None, title_size=Pt(24)):
    if title_text:
        if len(text_frame.paragraphs) > 0 and text_frame.paragraphs[0].text:
            p0 = text_frame.add_paragraph()
        else:
            p0 = text_frame.paragraphs[0]
        p0.space_after = Pt(6)
        r0 = p0.add_run()
        r0.text = title_text
        r0.font.name = FONT_TITLE
        r0.font.size = title_size
        r0.font.bold = True
        r0.font.color.rgb = COLOR_NAVY
        start_idx = 0
    else:
        start_idx = -1
        
    for idx, item in enumerate(items):
        if idx == 0 and start_idx == -1 and (len(text_frame.paragraphs) == 0 or not text_frame.paragraphs[0].text):
            p = text_frame.paragraphs[0]
        else:
            p = text_frame.add_paragraph()
        p.space_after = space_after
        r = p.add_run()
        r.text = f"* {item}"
        r.font.name = FONT_BODY
        r.font.size = font_size
        r.font.color.rgb = COLOR_NAVY

def build_all():
    prs = create_presentation()
    os.makedirs("slides", exist_ok=True)
    
    # =========================================================================
    # SLIDE 1 (Part A, Yellow)
    # =========================================================================
    s1 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s1)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s1, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Spring Batch: xử lý hàng triệu bản ghi an toàn",
        section="A"
    )
    draw_footer_and_progress(s1, 1, 16)
    
    tb_sub = s1.shapes.add_textbox(c_left, c_top, c_w, Inches(0.5))
    p = tb_sub.text_frame.paragraphs[0]
    r = p.add_run()
    r.text = "Cách hoạt động và khi nào nên dùng trong thực tế"
    r.font.name = FONT_BODY
    r.font.size = Pt(20)
    r.font.bold = True
    r.font.color.rgb = COLOR_NAVY
    
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
        
    draw_card(s1, c_left + Inches(5.6), c_top + Inches(0.7), Inches(5.7), Inches(4.0), COLOR_WHITE)
    s1.shapes.add_picture("slides/assets/icons/conveyor.png", c_left + Inches(6.0), c_top + Inches(1.1), Inches(2.2), Inches(2.2))
    s1.shapes.add_picture("slides/assets/icons/chunk_box.png", c_left + Inches(8.5), c_top + Inches(1.1), Inches(2.2), Inches(2.2))
    
    cap_box = s1.shapes.add_textbox(c_left + Inches(5.65), c_top + Inches(3.5), Inches(5.6), Inches(0.8))
    cp = cap_box.text_frame.paragraphs[0]
    cp.alignment = PP_ALIGN.CENTER
    cr = cp.add_run()
    cr.text = "Dây chuyền xử lý dữ liệu hàng loạt tự động"
    cr.font.name = FONT_TITLE
    cr.font.size = Pt(22)
    cr.font.color.rgb = COLOR_NAVY
    
    add_notes(s1, "Xin chào thầy cô và các bạn. Hôm nay nhóm em xin trình bày về Spring Batch, một framework chuyên dụng để xử lý khối lượng lớn dữ liệu an toàn và tin cậy. Chúng ta sẽ cùng tìm hiểu cách Spring Batch vận hành và khi nào nên áp dụng. Hãy bắt đầu với một bài toán thực tế rất gần gũi trong ngành ngân hàng.")

    # =========================================================================
    # SLIDE 2 (Part A, Yellow)
    # =========================================================================
    s2 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s2)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s2, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Cuối tháng, ngân hàng phải cộng lãi cho 5 triệu tài khoản",
        section="A"
    )
    draw_footer_and_progress(s2, 2, 16)
    
    # Intro card
    draw_card(s2, c_left, c_top, Inches(11.3), Inches(1.1), COLOR_CREAM)
    s2.shapes.add_picture("slides/assets/icons/bank.png", c_left + Inches(0.2), c_top + Inches(0.12), Inches(0.85), Inches(0.85))
    s2.shapes.add_picture("slides/assets/icons/coin.png", c_left + Inches(1.2), c_top + Inches(0.12), Inches(0.85), Inches(0.85))
    
    ibox = s2.shapes.add_textbox(c_left + Inches(2.2), c_top + Inches(0.15), Inches(8.9), Inches(0.85))
    ip = ibox.text_frame.paragraphs[0]
    ir = ip.add_run()
    ir.text = "Bài toán nghe có vẻ rất đơn giản: chỉ cần lặp qua từng tài khoản, tính số tiền lãi dựa trên số dư, rồi cộng tiền vào tài khoản."
    ir.font.name = FONT_BODY
    ir.font.size = Pt(17)
    ir.font.color.rgb = COLOR_NAVY
    
    # 3 characteristics cards
    col_w = Inches(3.55)
    gap = Inches(0.32)
    cols_data = [
        ("database", "Dữ liệu cực lớn", [
            "5 triệu tài khoản đang hoạt động",
            "Kích thước nhiều Gigabyte",
            "Không thể tải hết vào bộ nhớ"
        ]),
        ("clock", "Chạy định kỳ", [
            "Mỗi tháng một lần vào nửa đêm",
            "Khung giờ chạy được lên lịch sẵn",
            "Không có con người ngồi bấm tay"
        ]),
        ("card", "Không trả lời ngay", [
            "Chạy ngầm ở phía sau hệ thống",
            "Khách hàng không đứng đợi màn hình",
            "Ưu tiên độ chính xác tuyệt đối"
        ]),
    ]
    for i, (icon_name, col_title, items) in enumerate(cols_data):
        cx = c_left + Inches(i * (3.55 + 0.32))
        cy = c_top + Inches(1.3)
        draw_card(s2, cx, cy, col_w, Inches(2.7), COLOR_WHITE)
        s2.shapes.add_picture(f"slides/assets/icons/{icon_name}.png", cx + Inches(0.2), cy + Inches(0.2), Inches(0.8), Inches(0.8))
        
        cbox = s2.shapes.add_textbox(cx + Inches(1.1), cy + Inches(0.15), col_w - Inches(1.2), Inches(0.7))
        cp = cbox.text_frame.paragraphs[0]
        cr = cp.add_run()
        cr.text = col_title
        cr.font.name = FONT_TITLE
        cr.font.size = Pt(22)
        cr.font.bold = True
        cr.font.color.rgb = COLOR_NAVY
        
        lbox = s2.shapes.add_textbox(cx + Inches(0.2), cy + Inches(0.95), col_w - Inches(0.4), Inches(1.6))
        ltf = lbox.text_frame
        ltf.word_wrap = True
        add_bullet_list(ltf, items, font_size=Pt(15), space_after=Pt(4))
        
    # Bottom banner card
    draw_card(s2, c_left, c_top + Inches(4.15), Inches(11.3), Inches(0.75), COLOR_YELLOW)
    bbox = s2.shapes.add_textbox(c_left + Inches(0.3), c_top + Inches(4.2), Inches(10.7), Inches(0.65))
    bp = bbox.text_frame.paragraphs[0]
    bp.alignment = PP_ALIGN.CENTER
    br = bp.add_run()
    br.text = "Loại công việc xử lý dữ liệu lớn theo lô định kỳ này được gọi là Batch."
    br.font.name = FONT_TITLE
    br.font.size = Pt(24)
    br.font.bold = True
    br.font.color.rgb = COLOR_NAVY
    
    add_notes(s2, "Hãy hình dung vào đêm cuối tháng, ngân hàng phải tính và cộng tiền lãi cho 5 triệu tài khoản khách hàng. Công việc này có 3 đặc điểm: dữ liệu rất lớn, chạy định kỳ trong đêm và không đòi hỏi phản hồi tức thì. Trong ngành phần mềm, người ta gọi đây là xử lý theo lô hay Batch processing. Vậy nếu viết bằng vòng lặp thông thường thì sao?")

    # =========================================================================
    # SLIDE 3 (Part A, Yellow)
    # =========================================================================
    s3 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s3)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s3, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Viết bằng vòng lặp thường thì sẽ gặp 4 rắc rối lớn",
        section="A"
    )
    draw_footer_and_progress(s3, 3, 16)
    
    troubles = [
        ("gear", "1. Quá tải bộ nhớ", "Nạp 5 triệu tài khoản vào bộ nhớ RAM thì máy chủ sẽ lập tức bị sập (lỗi OutOfMemory)."),
        ("warning_triangle", "2. Dữ liệu hỏng", "Chỉ cần 1 tài khoản có số liệu lỗi, cả vòng lặp bị ngắt và dừng ngang giữa chừng."),
        ("restart_arrow", "3. Mất điện sự cố", "Mất điện ở tài khoản 3 triệu, chạy lại từ đầu thì 3 triệu người đầu bị cộng lãi 2 lần."),
        ("notebook", "4. Mù thông tin", "Không biết đang chạy đến đâu, đã xong bao nhiêu, còn bao lâu và bị lỗi ở dòng nào."),
    ]
    
    # 2x2 grid of cards
    card_w = Inches(5.45)
    card_h = Inches(1.85)
    for idx, (icon_name, title, desc) in enumerate(troubles):
        col = idx % 2
        row = idx // 2
        cx = c_left + Inches(col * (5.45 + 0.4))
        cy = c_top + Inches(row * (1.85 + 0.2))
        
        draw_card(s3, cx, cy, card_w, card_h, COLOR_WHITE)
        s3.shapes.add_picture(f"slides/assets/icons/{icon_name}.png", cx + Inches(0.2), cy + Inches(0.2), Inches(0.85), Inches(0.85))
        
        tbox = s3.shapes.add_textbox(cx + Inches(1.15), cy + Inches(0.15), card_w - Inches(1.3), Inches(0.5))
        tp = tbox.text_frame.paragraphs[0]
        tr = tp.add_run()
        tr.text = title
        tr.font.name = FONT_TITLE
        tr.font.size = Pt(22)
        tr.font.bold = True
        tr.font.color.rgb = COLOR_CORAL
        
        dbox = s3.shapes.add_textbox(cx + Inches(1.15), cy + Inches(0.65), card_w - Inches(1.3), Inches(1.1))
        dtf = dbox.text_frame
        dtf.word_wrap = True
        dp = dtf.paragraphs[0]
        dr = dp.add_run()
        dr.text = desc
        dr.font.name = FONT_BODY
        dr.font.size = Pt(16)
        dr.font.color.rgb = COLOR_NAVY
        
    # Bottom punchline banner
    draw_card(s3, c_left, c_top + Inches(4.15), Inches(11.3), Inches(0.75), COLOR_YELLOW)
    pbox = s3.shapes.add_textbox(c_left + Inches(0.3), c_top + Inches(4.2), Inches(10.7), Inches(0.65))
    pp = pbox.text_frame.paragraphs[0]
    pp.alignment = PP_ALIGN.CENTER
    pr = pp.add_run()
    pr.text = "Đây chính là những việc Spring Batch làm sẵn cho chúng ta."
    pr.font.name = FONT_TITLE
    pr.font.size = Pt(24)
    pr.font.bold = True
    pr.font.color.rgb = COLOR_NAVY
    
    add_notes(s3, "Nếu chỉ dùng một vòng lặp for đơn giản, bạn sẽ đối mặt với 4 cơn ác mộng: bộ nhớ RAM cạn kiệt làm sập máy, một bản ghi bẩn làm dừng cả đợt, mất điện khiến dữ liệu bị trùng lặp khi chạy lại, và không biết tiến độ tới đâu. Tự giải quyết những rắc rối này rất phức tạp. May mắn là Spring Batch đã giải quyết sẵn tất cả.")

    # =========================================================================
    # SLIDE 4 (Part B, Mint)
    # =========================================================================
    s4 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s4)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s4, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Spring Batch: bộ công cụ có sẵn cho đúng 4 rắc rối đó",
        section="B"
    )
    draw_footer_and_progress(s4, 4, 16)
    
    # Intro card
    draw_card(s4, c_left, c_top, Inches(11.3), Inches(0.7), COLOR_CREAM)
    ibox = s4.shapes.add_textbox(c_left + Inches(0.2), c_top + Inches(0.08), Inches(10.9), Inches(0.55))
    ip = ibox.text_frame.paragraphs[0]
    ir = ip.add_run()
    ir.text = "Framework tiêu chuẩn của hệ sinh thái Spring chuyên biệt cho xử lý dữ liệu hàng loạt."
    ir.font.name = FONT_BODY
    ir.font.size = Pt(18)
    ir.font.bold = True
    ir.font.color.rgb = COLOR_NAVY
    
    # Mapping Table Card (Rắc rối -> Giải pháp Spring Batch)
    draw_card(s4, c_left, c_top + Inches(0.9), Inches(11.3), Inches(2.9), COLOR_WHITE)
    tbox = s4.shapes.add_textbox(c_left + Inches(0.3), c_top + Inches(1.0), Inches(10.7), Inches(2.7))
    ttf = tbox.text_frame
    ttf.word_wrap = True
    
    rows = [
        ("Nhiều dữ liệu (RAM quá tải)", "Xử lý theo từng nhóm nhỏ (Chunk Processing)", "chunk_box"),
        ("Một bản ghi hỏng dừng cả đợt", "Tự động bỏ qua và ghi nhật ký lỗi (Skip)", "warning_triangle"),
        ("Mất điện sự cố giữa chừng", "Ghi nhớ mốc an toàn, chạy tiếp mượt mà (Restart)", "restart_arrow"),
        ("Không biết tiến độ và trạng thái", "Sổ ghi chép chi tiết tự động vào database (JobRepository)", "notebook"),
    ]
    for idx, (problem, solution, icon_name) in enumerate(rows):
        p = ttf.paragraphs[0] if idx == 0 else ttf.add_paragraph()
        p.space_after = Pt(7)
        r1 = p.add_run()
        r1.text = f"{idx+1}. {problem} -> "
        r1.font.name = FONT_BODY
        r1.font.size = Pt(16)
        r1.font.bold = True
        r1.font.color.rgb = COLOR_NAVY
        
        r2 = p.add_run()
        r2.text = solution
        r2.font.name = FONT_BODY
        r2.font.size = Pt(16)
        r2.font.color.rgb = COLOR_CORAL if idx in [1, 2] else COLOR_NAVY
        
    # Honest remark card
    draw_card(s4, c_left, c_top + Inches(4.0), Inches(11.3), Inches(0.9), COLOR_CREAM)
    rbox = s4.shapes.add_textbox(c_left + Inches(0.3), c_top + Inches(4.08), Inches(10.7), Inches(0.75))
    rp = rbox.text_frame.paragraphs[0]
    rr = rp.add_run()
    rr.text = "Nhận xét trung thực: tự viết code cũng làm được, nhưng bạn sẽ phải tự xây dựng lại toàn bộ phần ghi nhớ tiến độ, cơ chế bỏ qua lỗi và hệ thống số liệu thống kê."
    rr.font.name = FONT_BODY
    rr.font.size = Pt(16)
    rr.font.color.rgb = COLOR_NAVY
    
    add_notes(s4, "Spring Batch là framework mã nguồn mở trưởng thành của hệ sinh thái Spring. Nó giải quyết triệt để 4 rắc rối trên bằng các cơ chế: chia nhóm chunk, bỏ qua lỗi skip, chạy tiếp restart và sổ ghi chép JobRepository. Thay vì tự phát minh lại bánh xe, chúng ta dùng bộ công cụ đã được tôi luyện. Giờ hãy xem Spring Batch tổ chức dây chuyền xử lý như thế nào.")

    # =========================================================================
    # SLIDE 5 (Part B, Mint)
    # =========================================================================
    s5 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s5)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s5, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Dây chuyền 3 khâu: đọc, xử lý và ghi",
        section="B"
    )
    draw_footer_and_progress(s5, 5, 16)
    
    # Analogy explanation card
    draw_card(s5, c_left, c_top, Inches(11.3), Inches(1.0), COLOR_CREAM)
    abox = s5.shapes.add_textbox(c_left + Inches(0.3), c_top + Inches(0.1), Inches(10.7), Inches(0.8))
    ap = abox.text_frame.paragraphs[0]
    ar = ap.add_run()
    ar.text = "Mô hình nhà máy đóng gói: nguyên liệu thô được đưa vào qua khâu Lấy hàng, chuyển sang khâu Kiểm tra dán nhãn, rồi đưa vào khâu Đóng thùng lưu kho."
    ar.font.name = FONT_BODY
    ar.font.size = Pt(17)
    ar.font.color.rgb = COLOR_NAVY
    
    # 3 Steps side-by-side with arrows
    box_w = Inches(3.3)
    arrow_w = Inches(0.6)
    gap = Inches(0.1)
    
    stages = [
        ("ItemReader", "KHÂU ĐỌC", "database", [
            "Lấy hàng từ kho",
            "Đọc từng tài khoản từ database",
            "Phân trang tự động theo từng khối"
        ]),
        ("ItemProcessor", "KHÂU XỬ LÝ", "gear", [
            "Kiểm tra và dán nhãn",
            "Tính toán số tiền lãi theo công thức",
            "Loại bỏ bản ghi không hợp lệ"
        ]),
        ("ItemWriter", "KHÂU GHI", "card", [
            "Xếp vào kho thành phẩm",
            "Cập nhật số dư mới vào database",
            "Ghi trọn vẹn cả lô trong 1 giao dịch"
        ]),
    ]
    
    for i, (eng_name, vn_name, icon_name, items) in enumerate(stages):
        bx = c_left + Inches(i * (3.3 + 0.7))
        by = c_top + Inches(1.2)
        
        draw_card(s5, bx, by, box_w, Inches(3.5), COLOR_WHITE)
        
        # Header banner inside box
        sb = s5.shapes.add_shape(MSO_SHAPE.RECTANGLE, bx, by, box_w, Inches(0.65))
        sb.fill.solid()
        sb.fill.fore_color.rgb = COLOR_MINT
        sb.line.color.rgb = COLOR_NAVY
        sb.line.width = Pt(2)
        
        sp = sb.text_frame.paragraphs[0]
        sp.alignment = PP_ALIGN.CENTER
        sr = sp.add_run()
        sr.text = eng_name
        sr.font.name = FONT_TITLE
        sr.font.size = Pt(22)
        sr.font.bold = True
        sr.font.color.rgb = COLOR_NAVY
        
        # Icon
        s5.shapes.add_picture(f"slides/assets/icons/{icon_name}.png", bx + Inches(1.2), by + Inches(0.8), Inches(0.9), Inches(0.9))
        
        # Bullet items
        lbox = s5.shapes.add_textbox(bx + Inches(0.15), by + Inches(1.85), box_w - Inches(0.3), Inches(1.5))
        ltf = lbox.text_frame
        ltf.word_wrap = True
        add_bullet_list(ltf, items, font_size=Pt(15), space_after=Pt(4))
        
        # Arrow between blocks (for i=0 and i=1)
        if i < 2:
            ax = bx + box_w + Inches(0.05)
            s5.shapes.add_picture("slides/assets/icons/pixel_arrow_right.png", ax, by + Inches(1.4), Inches(0.6), Inches(0.6))
            
    add_notes(s5, "Mỗi bước xử lý trong Spring Batch được chuẩn hóa thành 3 khâu: Reader, Processor và Writer. Reader chịu trách nhiệm lấy từng bản ghi, Processor tính toán biến đổi dữ liệu, và Writer nhận cả lô đã xử lý để ghi xuống kho. Sự phân chia rành mạch này giúp code gọn gàng, dễ kiểm thử và rất dễ bảo trì. Nhiều dây chuyền như vậy ghép lại sẽ tạo thành một công việc hoàn chỉnh.")

    # =========================================================================
    # SLIDE 6 (Part B, Mint)
    # =========================================================================
    s6 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s6)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s6, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Step là một dây chuyền, Job là cả nhà máy",
        section="B"
    )
    draw_footer_and_progress(s6, 6, 16)
    
    # Analogy card
    draw_card(s6, c_left, c_top, Inches(11.3), Inches(0.95), COLOR_CREAM)
    abox = s6.shapes.add_textbox(c_left + Inches(0.3), c_top + Inches(0.1), Inches(10.7), Inches(0.75))
    ap = abox.text_frame.paragraphs[0]
    ar = ap.add_run()
    ar.text = "Nhà máy (Job) gồm nhiều dây chuyền (Step) chạy nối tiếp. Step trước hoàn tất thành công thì Step sau mới được kích hoạt để đảm bảo an toàn tuyệt đối."
    ar.font.name = FONT_BODY
    ar.font.size = Pt(17)
    ar.font.color.rgb = COLOR_NAVY
    
    # Outer frame representing "Job: Tính lãi cuối tháng"
    job_frame_top = c_top + Inches(1.15)
    draw_card(s6, c_left, job_frame_top, Inches(11.3), Inches(2.6), COLOR_WHITE)
    
    jlbl = s6.shapes.add_textbox(c_left + Inches(0.3), job_frame_top + Inches(0.1), Inches(10.7), Inches(0.45))
    jp = jlbl.text_frame.paragraphs[0]
    jr = jp.add_run()
    jr.text = "JOB: TÍNH LÃI VÀ BÁO CÁO CUỐI THÁNG"
    jr.font.name = FONT_TITLE
    jr.font.size = Pt(24)
    jr.font.bold = True
    jr.font.color.rgb = COLOR_NAVY
    
    # 3 Steps cards inside Job
    step_w = Inches(3.2)
    steps_info = [
        ("Step 1: Tính lãi", "database", "Reader -> Processor -> Writer cho 5 triệu tài khoản"),
        ("Step 2: Thông báo", "notebook", "Tạo thư thông báo biến động số dư cho khách hàng"),
        ("Step 3: Báo cáo", "paper_stack", "Tổng hợp số liệu xuất báo cáo gửi ban giám đốc"),
    ]
    for idx, (s_title, icon_name, s_desc) in enumerate(steps_info):
        sx = c_left + Inches(0.3 + idx * (3.2 + 0.55))
        sy = job_frame_top + Inches(0.65)
        draw_card(s6, sx, sy, step_w, Inches(1.75), COLOR_CREAM)
        
        s6.shapes.add_picture(f"slides/assets/icons/{icon_name}.png", sx + Inches(0.15), sy + Inches(0.15), Inches(0.6), Inches(0.6))
        stbox = s6.shapes.add_textbox(sx + Inches(0.8), sy + Inches(0.15), step_w - Inches(0.85), Inches(0.45))
        stp = stbox.text_frame.paragraphs[0]
        strun = stp.add_run()
        strun.text = s_title
        strun.font.name = FONT_TITLE
        strun.font.size = Pt(20)
        strun.font.bold = True
        strun.font.color.rgb = COLOR_NAVY
        
        sdbox = s6.shapes.add_textbox(sx + Inches(0.15), sy + Inches(0.8), step_w - Inches(0.3), Inches(0.85))
        sdtf = sdbox.text_frame
        sdtf.word_wrap = True
        sdp = sdtf.paragraphs[0]
        sdr = sdp.add_run()
        sdr.text = s_desc
        sdr.font.name = FONT_BODY
        sdr.font.size = Pt(15)
        sdr.font.color.rgb = COLOR_NAVY
        
        if idx < 2:
            ax = sx + step_w + Inches(0.08)
            s6.shapes.add_picture("slides/assets/icons/pixel_arrow_right.png", ax, sy + Inches(0.6), Inches(0.4), Inches(0.4))
            
    # Bottom safety rule banner
    draw_card(s6, c_left, c_top + Inches(3.95), Inches(11.3), Inches(0.95), COLOR_YELLOW)
    sbox = s6.shapes.add_textbox(c_left + Inches(0.3), c_top + Inches(4.05), Inches(10.7), Inches(0.75))
    sp = sbox.text_frame.paragraphs[0]
    sr = sp.add_run()
    sr.text = "Quy tắc an toàn: Nếu Step 1 bị lỗi, các Step sau sẽ không chạy tiếp để tránh gửi thông báo sai hoặc xuất báo cáo bẩn."
    sr.font.name = FONT_BODY
    sr.font.size = Pt(17)
    sr.font.bold = True
    sr.font.color.rgb = COLOR_NAVY
    
    add_notes(s6, "Một Job tương đương với một ca làm việc lớn của nhà máy, bao gồm nhiều Step nối tiếp nhau. Ví dụ ở ngân hàng: Step 1 tính lãi xong thì Step 2 mới gửi thông báo, rồi Step 3 xuất báo cáo. Nếu Step tính lãi bị hỏng, hệ thống sẽ dừng ngay lập tức, không gửi thông báo sai cho khách. Vậy trong mỗi Step, dữ liệu được chia nhỏ ra sao? Đó là khái niệm Chunk.")

    # =========================================================================
    # SLIDE 7 (Part B, Mint)
    # =========================================================================
    s7 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s7)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s7, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Chunk: chấm từng xấp bài, không ôm cả núi bài",
        section="B"
    )
    draw_footer_and_progress(s7, 7, 16)
    
    draw_card(s7, c_left, c_top, Inches(11.3), Inches(1.2), COLOR_CREAM)
    s7.shapes.add_picture("slides/assets/icons/paper_stack.png", c_left + Inches(0.25), c_top + Inches(0.12), Inches(0.95), Inches(0.95))
    abox = s7.shapes.add_textbox(c_left + Inches(1.35), c_top + Inches(0.12), Inches(9.7), Inches(0.95))
    ap = abox.text_frame.paragraphs[0]
    ar = ap.add_run()
    ar.text = "Ví dụ chấm thi: cô giáo chia 500 bài thi thành từng xấp 20 bài. Chấm xong mỗi xấp thì ghi điểm ngay vào sổ. Nếu có sự cố, cô chỉ cần chấm lại xấp đang dở, không phải chấm lại từ đầu."
    ar.font.name = FONT_BODY
    ar.font.size = Pt(17)
    ar.font.color.rgb = COLOR_NAVY
    
    diag_top = c_top + Inches(1.4)
    draw_card(s7, c_left, diag_top, Inches(5.5), Inches(3.6), COLOR_WHITE)
    dbox = s7.shapes.add_textbox(c_left + Inches(0.3), diag_top + Inches(0.15), Inches(4.9), Inches(3.3))
    dtf = dbox.text_frame
    dtf.word_wrap = True
    bullets7_1 = [
        "Reader đọc từng bản ghi cho đủ N mục",
        "Processor nhận từng mục và xử lý",
        "Writer nhận trọn vẹn N mục đã xử lý",
        "Commit một giao dịch duy nhất cho cả xấp"
    ]
    add_bullet_list(dtf, bullets7_1, font_size=Pt(17), space_after=Pt(5), title_text="Cơ chế xử lý theo Chunk (N bản ghi):")
    
    draw_card(s7, c_left + Inches(5.8), diag_top, Inches(5.5), Inches(3.6), COLOR_WHITE)
    tbox = s7.shapes.add_textbox(c_left + Inches(6.1), diag_top + Inches(0.15), Inches(4.9), Inches(3.3))
    ttf = tbox.text_frame
    ttf.word_wrap = True
    bullets7_2 = [
        "Xấp quá nhỏ: lưu liên tục tốn công mở giao dịch",
        "Xấp quá lớn: tốn bộ nhớ RAM, nếu lỗi phải làm lại nhiều",
        "Thực tế: thường chọn từ 20 đến 500 bản ghi",
        "Mỗi xấp hoàn tất là một mốc an toàn được lưu chắc"
    ]
    add_bullet_list(ttf, bullets7_2, font_size=Pt(17), space_after=Pt(5), title_text="Lựa chọn kích thước Chunk phù hợp:")
    
    add_notes(s7, "Thay vì ôm trọn cả núi 5 triệu tài khoản, Spring Batch chia nhỏ thành từng xấp gọi là chunk. Giống như cô giáo chấm từng xấp 20 bài thi rồi ghi điểm vào sổ một lần. Cách này giúp bộ nhớ máy luôn nhẹ và mỗi xấp commit là một bước tiến chắc chắn. Vậy hệ thống làm sao nhớ đã chấm đến xấp nào, chúng ta cùng xem tiếp.")

    # =========================================================================
    # SLIDE 8 (Part B, Mint)
    # =========================================================================
    s8 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s8)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s8, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Sổ ghi chép: Spring Batch nhớ mình đã làm tới đâu",
        section="B"
    )
    draw_footer_and_progress(s8, 8, 16)
    
    draw_card(s8, c_left, c_top, Inches(11.3), Inches(1.0), COLOR_CREAM)
    s8.shapes.add_picture("slides/assets/icons/notebook.png", c_left + Inches(0.25), c_top + Inches(0.1), Inches(0.8), Inches(0.8))
    abox = s8.shapes.add_textbox(c_left + Inches(1.2), c_top + Inches(0.1), Inches(9.8), Inches(0.8))
    ap = abox.text_frame.paragraphs[0]
    ar = ap.add_run()
    ar.text = "Như cuốn sổ nhật ký công trường: Spring Batch tự động ghi chép chi tiết mỗi lần chạy, số lượng bản ghi đã đọc, đã ghi và trạng thái thành công hay lỗi."
    ar.font.name = FONT_BODY
    ar.font.size = Pt(17)
    ar.font.color.rgb = COLOR_NAVY
    
    card_top = c_top + Inches(1.2)
    # Left Card: Core concepts
    draw_card(s8, c_left, card_top, Inches(5.5), Inches(3.8), COLOR_WHITE)
    lbox = s8.shapes.add_textbox(c_left + Inches(0.3), card_top + Inches(0.15), Inches(4.9), Inches(3.5))
    ltf = lbox.text_frame
    ltf.word_wrap = True
    core_items = [
        "JobLauncher: nút bấm khởi chạy công việc",
        "JobRepository: cuốn sổ ghi chép các bảng BATCH",
        "JobInstance: công việc cụ thể (ví dụ: Tính lãi tháng 10)",
        "JobExecution: mỗi lần bấm máy (chạy lại là một execution mới của cùng instance)"
    ]
    add_bullet_list(ltf, core_items, font_size=Pt(16), space_after=Pt(6), title_text="Các khái niệm cốt lõi:")
    
    # Right Card: Status & Metrics
    draw_card(s8, c_left + Inches(5.8), card_top, Inches(5.5), Inches(3.8), COLOR_WHITE)
    rbox = s8.shapes.add_textbox(c_left + Inches(6.1), card_top + Inches(0.15), Inches(4.9), Inches(3.5))
    rtf = rbox.text_frame
    rtf.word_wrap = True
    status_items = [
        "Trạng thái chuẩn: STARTED, COMPLETED, FAILED",
        "readCount: tổng số bản ghi đã đọc từ database",
        "writeCount: tổng số bản ghi đã ghi thành công",
        "commitCount: số lần lưu giao dịch thành công",
        "skipCount: số bản ghi lỗi được bỏ qua an toàn"
    ]
    add_bullet_list(rtf, status_items, font_size=Pt(16), space_after=Pt(5), title_text="Trạng thái và số liệu theo dõi:")
    
    add_notes(s8, "Để không bao giờ bị mất dấu tiến độ, Spring Batch tự động ghi chép nhật ký vào các bảng cơ sở dữ liệu thông qua JobRepository. Khái niệm JobInstance đại diện cho công việc nghiệp vụ, còn JobExecution là từng lần bấm máy thực tế. Mọi số liệu như đọc bao nhiêu, ghi bao nhiêu, lỗi bao nhiêu đều được lưu vết. Nhờ cuốn sổ này mà khi gặp sự cố, hệ thống có thể phục hồi dễ dàng.")

    # =========================================================================
    # SLIDE 9 (Part B, Mint)
    # =========================================================================
    s9 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s9)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s9, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Gặp sự cố thì sao: Skip, Retry và Restart",
        section="B"
    )
    draw_footer_and_progress(s9, 9, 16)
    
    # 3 Cards side by side
    col_w = Inches(3.55)
    gap = Inches(0.32)
    mechanisms = [
        ("warning_triangle", "Skip (Bỏ qua)", [
            "Một tài khoản bị lỗi dữ liệu",
            "Bỏ qua và ghi chép vào nhật ký",
            "Cả đợt 5 triệu người vẫn tiếp tục"
        ]),
        ("clock", "Retry (Thử lại)", [
            "Gọi dịch vụ ngoài bị nghẽn mạng",
            "Thử lại vài lần với độ trễ ngắn",
            "Tính năng có sẵn của framework"
        ]),
        ("restart_arrow", "Restart (Chạy tiếp)", [
            "Mất điện ở tài khoản thứ 3 triệu",
            "Khi bật lại, đọc mốc trong sổ",
            "Chạy tiếp từ xấp đã lưu chắc"
        ]),
    ]
    for i, (icon_name, title, items) in enumerate(mechanisms):
        cx = c_left + Inches(i * (3.55 + 0.32))
        cy = c_top + Inches(0.1)
        draw_card(s9, cx, cy, col_w, Inches(3.6), COLOR_WHITE)
        
        # Header banner
        sb = s9.shapes.add_shape(MSO_SHAPE.RECTANGLE, cx, cy, col_w, Inches(0.6))
        sb.fill.solid()
        sb.fill.fore_color.rgb = COLOR_CORAL if i == 0 else (COLOR_YELLOW if i == 1 else COLOR_MINT)
        sb.line.color.rgb = COLOR_NAVY
        sb.line.width = Pt(2)
        
        sp = sb.text_frame.paragraphs[0]
        sp.alignment = PP_ALIGN.CENTER
        sr = sp.add_run()
        sr.text = title
        sr.font.name = FONT_TITLE
        sr.font.size = Pt(22)
        sr.font.bold = True
        sr.font.color.rgb = COLOR_NAVY
        
        # Icon
        s9.shapes.add_picture(f"slides/assets/icons/{icon_name}.png", cx + Inches(1.35), cy + Inches(0.75), Inches(0.85), Inches(0.85))
        
        # Bullets
        lbox = s9.shapes.add_textbox(cx + Inches(0.15), cy + Inches(1.75), col_w - Inches(0.3), Inches(1.6))
        ltf = lbox.text_frame
        ltf.word_wrap = True
        add_bullet_list(ltf, items, font_size=Pt(15), space_after=Pt(5))
        
    # Bottom warning card
    draw_card(s9, c_left, c_top + Inches(3.9), Inches(11.3), Inches(1.1), COLOR_CREAM)
    s9.shapes.add_picture("slides/assets/icons/warning_triangle.png", c_left + Inches(0.25), c_top + Inches(4.05), Inches(0.75), Inches(0.75))
    wbox = s9.shapes.add_textbox(c_left + Inches(1.15), c_top + Inches(3.95), Inches(9.8), Inches(1.0))
    wp = wbox.text_frame.paragraphs[0]
    wr = wp.add_run()
    wr.text = "Khung cảnh báo: Restart chỉ an toàn khi bước ghi không tạo trùng lặp dữ liệu (ghi theo khóa chính, kiểu cập nhật thay vì cộng dồn số học)."
    wr.font.name = FONT_BODY
    wr.font.size = Pt(16)
    wr.font.bold = True
    wr.font.color.rgb = COLOR_NAVY
    
    add_notes(s9, "Khi chạy lượng dữ liệu khổng lồ, sự cố chắc chắn sẽ xảy ra. Spring Batch trang bị bộ ba phòng vệ: Skip bỏ qua bản ghi rác, Retry thử lại khi nghẽn mạng, và Restart chạy tiếp từ mốc dừng khi mất điện. Lưu ý bước ghi cần thiết kế kiểu cập nhật để chạy lại an toàn. Giờ hãy xem một Job trong mã nguồn.")

    # =========================================================================
    # SLIDE 10 (Part B, Mint)
    # =========================================================================
    s10 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s10)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s10, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Một Job trông như thế nào trong code thực tế",
        section="B"
    )
    draw_footer_and_progress(s10, 10, 16)
    
    # Left Card: Real code snippet from TrendingPostsJobConfig.java (11 lines, 16pt monospace)
    code_card_w = Inches(6.8)
    draw_card(s10, c_left, c_top, code_card_w, Inches(5.0), COLOR_CREAM)
    
    # Header tab on code box
    c_tab = s10.shapes.add_shape(MSO_SHAPE.RECTANGLE, c_left, c_top, Inches(3.5), Inches(0.45))
    c_tab.fill.solid()
    c_tab.fill.fore_color.rgb = COLOR_YELLOW
    c_tab.line.color.rgb = COLOR_NAVY
    c_tab.line.width = Pt(2)
    cp = c_tab.text_frame.paragraphs[0]
    cr = cp.add_run()
    cr.text = "Cấu hình thật: Spring Batch 6"
    cr.font.name = FONT_TITLE
    cr.font.size = Pt(18)
    cr.font.bold = True
    cr.font.color.rgb = COLOR_NAVY
    
    cbox = s10.shapes.add_textbox(c_left + Inches(0.2), c_top + Inches(0.55), code_card_w - Inches(0.4), Inches(4.3))
    ctf = cbox.text_frame
    ctf.word_wrap = False
    
    code_lines = [
        "@Bean",
        "public Step trendingStep(JobRepository repo, TxManager tx) {",
        "    return new StepBuilder(\"trendingPostsStep\", repo)",
        "        .<PostView, TrendingPost>chunk(20, tx)",
        "        .reader(trendingPostReader())",
        "        .processor(trendingPostProcessor)",
        "        .writer(trendingPostWriter)",
        "        .faultTolerant().skip(MalformedException.class)",
        "        .skipLimit(100).build();",
        "}",
        "@Bean",
        "public Job trendingJob(JobRepository repo, Step step) {",
        "    return new JobBuilder(\"trendingPostsJob\", repo)",
        "        .start(step).build();",
        "}",
    ]
    # Let's compress slightly to exactly 11 compact lines:
    code_lines_compact = [
        "@Bean",
        "public Step trendingStep(JobRepository repo, TxManager tx) {",
        "  return new StepBuilder(\"trendingPostsStep\", repo)",
        "    .<PostView, TrendingPost>chunk(20, tx)",
        "    .reader(reader()).processor(proc).writer(writer)",
        "    .faultTolerant().skip(MalformedException.class)",
        "    .skipLimit(100).build();",
        "}",
        "@Bean",
        "public Job trendingJob(JobRepository repo, Step step) {",
        "  return new JobBuilder(\"trendingPostsJob\", repo)",
        "    .start(step).build();",
        "}",
    ]
    for idx, cl in enumerate(code_lines_compact):
        p = ctf.paragraphs[0] if idx == 0 else ctf.add_paragraph()
        p.space_after = Pt(2)
        r = p.add_run()
        r.text = cl
        r.font.name = FONT_CODE
        r.font.size = Pt(15.5)
        r.font.bold = True
        r.font.color.rgb = COLOR_NAVY
        
    # Right Card: Colored annotations mapping code lines to concepts (slides 5 to 9)
    anno_card_w = Inches(4.2)
    draw_card(s10, c_left + Inches(7.1), c_top, anno_card_w, Inches(5.0), COLOR_WHITE)
    
    abox = s10.shapes.add_textbox(c_left + Inches(7.3), c_top + Inches(0.2), anno_card_w - Inches(0.4), Inches(4.6))
    atf = abox.text_frame
    atf.word_wrap = True
    
    annos = [
        (".chunk(20, tx)", "Xử lý theo nhóm 20 bản ghi một giao dịch (Slide 7)"),
        ("reader / processor / writer", "Dây chuyền chuẩn hóa 3 khâu đọc, xử lý và ghi (Slide 5)"),
        (".faultTolerant() .skip()", "Bật chịu lỗi, tự bỏ qua dữ liệu hỏng tối đa 100 lần (Slide 9)"),
        ("StepBuilder / JobBuilder", "Khai báo Step dây chuyền và Job cả nhà máy (Slide 6)"),
        ("repo (JobRepository)", "Tự động ghi chép tiến độ vào database (Slide 8)"),
    ]
    for idx, (label, desc) in enumerate(annos):
        p1 = atf.paragraphs[0] if idx == 0 else atf.add_paragraph()
        p1.space_after = Pt(2)
        r1 = p1.add_run()
        r1.text = f"{label}"
        r1.font.name = FONT_CODE
        r1.font.size = Pt(15)
        r1.font.bold = True
        r1.font.color.rgb = COLOR_CORAL
        
        p2 = atf.add_paragraph()
        p2.space_after = Pt(10)
        r2 = p2.add_run()
        r2.text = f"= {desc}"
        r2.font.name = FONT_BODY
        r2.font.size = Pt(14)
        r2.font.color.rgb = COLOR_NAVY
        
    add_notes(s10, "Đây là cấu hình thực tế của một Job trong Spring Batch 6. Cú pháp Builder rất trực quan: khai báo Step với chunk 20, gắn reader, processor, writer, bật chịu lỗi skip bản ghi hỏng, rồi gắn Step vào Job. Mọi khái niệm lý thuyết đều ánh xạ chính xác vào từng dòng code này. Tiếp theo, khi nào nên và không nên dùng Spring Batch?")

    # =========================================================================
    # SLIDE 11 (Part B, Mint)
    # =========================================================================
    s11 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s11)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s11, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Khi nào nên dùng Spring Batch, khi nào không nên",
        section="B"
    )
    draw_footer_and_progress(s11, 11, 16)
    
    col_w = Inches(5.45)
    # Left Column: When to use
    draw_card(s11, c_left, c_top, col_w, Inches(3.8), COLOR_WHITE)
    sb_left = s11.shapes.add_shape(MSO_SHAPE.RECTANGLE, c_left, c_top, col_w, Inches(0.6))
    sb_left.fill.solid()
    sb_left.fill.fore_color.rgb = COLOR_MINT
    sb_left.line.color.rgb = COLOR_NAVY
    sb_left.line.width = Pt(2)
    sp = sb_left.text_frame.paragraphs[0]
    sp.alignment = PP_ALIGN.CENTER
    sr = sp.add_run()
    sr.text = "NÊN DÙNG (RẤT PHÙ HỢP)"
    sr.font.name = FONT_TITLE
    sr.font.size = Pt(22)
    sr.font.bold = True
    sr.font.color.rgb = COLOR_NAVY
    
    lbox = s11.shapes.add_textbox(c_left + Inches(0.2), c_top + Inches(0.75), col_w - Inches(0.4), Inches(2.9))
    ltf = lbox.text_frame
    ltf.word_wrap = True
    should_use = [
        "Tính toán định kỳ hàng triệu bản ghi (tính lãi, tính lương)",
        "Gửi email hoặc thông báo hàng loạt cho người dùng",
        "Tổng hợp báo cáo tài chính và thống kê cuối ngày",
        "Đồng bộ, chuyển đổi dữ liệu giữa các hệ thống",
        "Tính trước điểm gợi ý hoặc bảng xếp hạng nội dung"
    ]
    add_bullet_list(ltf, should_use, font_size=Pt(15), space_after=Pt(5))
    
    # Right Column: When NOT to use
    draw_card(s11, c_left + Inches(5.85), c_top, col_w, Inches(3.8), COLOR_WHITE)
    sb_right = s11.shapes.add_shape(MSO_SHAPE.RECTANGLE, c_left + Inches(5.85), c_top, col_w, Inches(0.6))
    sb_right.fill.solid()
    sb_right.fill.fore_color.rgb = COLOR_CORAL
    sb_right.line.color.rgb = COLOR_NAVY
    sb_right.line.width = Pt(2)
    sp = sb_right.text_frame.paragraphs[0]
    sp.alignment = PP_ALIGN.CENTER
    sr = sp.add_run()
    sr.text = "KHÔNG NÊN DÙNG"
    sr.font.name = FONT_TITLE
    sr.font.size = Pt(22)
    sr.font.bold = True
    sr.font.color.rgb = COLOR_NAVY
    
    rbox = s11.shapes.add_textbox(c_left + Inches(6.05), c_top + Inches(0.75), col_w - Inches(0.4), Inches(2.9))
    rtf = rbox.text_frame
    rtf.word_wrap = True
    should_not_use = [
        "Cần trả lời ngay khi người dùng bấm nút (nhắn tin, đăng nhập)",
        "Xử lý luồng sự kiện liên tục theo thời gian thực (streaming)",
        "Việc nhỏ vài trăm bản ghi (chỉ cần tác vụ định kỳ đơn giản)",
        "Công việc không cần ghi nhớ tiến độ hoặc phục hồi sự cố"
    ]
    add_bullet_list(rtf, should_not_use, font_size=Pt(15), space_after=Pt(6))
    
    # Bottom Rule of Thumb Card
    draw_card(s11, c_left, c_top + Inches(4.0), Inches(11.3), Inches(0.95), COLOR_YELLOW)
    s11.shapes.add_picture("slides/assets/icons/gear.png", c_left + Inches(0.2), c_top + Inches(4.1), Inches(0.75), Inches(0.75))
    rot_box = s11.shapes.add_textbox(c_left + Inches(1.1), c_top + Inches(4.1), Inches(10.0), Inches(0.75))
    rp = rot_box.text_frame.paragraphs[0]
    rr = rp.add_run()
    rr.text = "Quy tắc ngón tay cái: Dữ liệu lớn, chạy định kỳ, không cần phản hồi tức thì, cần chịu lỗi và theo dõi chặt chẽ."
    rr.font.name = FONT_TITLE
    rr.font.size = Pt(22)
    rr.font.bold = True
    rr.font.color.rgb = COLOR_NAVY
    
    add_notes(s11, "Spring Batch rất mạnh mẽ nhưng không phải vạn năng. Chúng ta nên dùng khi xử lý dữ liệu lớn định kỳ, cần chịu lỗi và lưu vết tiến độ như tính lương, tính lãi, gửi thông báo. Ngược lại, nếu cần phản hồi tức thì hay dữ liệu nhỏ vài trăm dòng thì không nên dùng. Hãy cùng tóm tắt 5 điểm cốt lõi trước khi đến phần demo.")

    # =========================================================================
    # SLIDE 12 (Part B, Mint)
    # =========================================================================
    s12 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s12)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s12, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Tóm tắt: 5 điều cần nhớ về Spring Batch",
        section="B"
    )
    draw_footer_and_progress(s12, 12, 16)
    
    # 5 pixel cards arranged horizontally
    summary_cards = [
        ("Job và Step", "Cấu trúc nhà máy", "conveyor", "Job gồm nhiều Step chạy nối tiếp theo thứ tự an toàn"),
        ("3 Khâu Chuẩn", "Reader, Proc, Writer", "gear", "Đọc bản ghi, xử lý biến đổi và ghi theo lô"),
        ("Chunk", "Chia nhỏ dữ liệu", "chunk_box", "Xử lý theo từng xấp nhỏ, commit từng giao dịch chắc chắn"),
        ("Sổ Ghi Chép", "JobRepository", "notebook", "Tự động ghi nhớ trạng thái và số liệu vào database"),
        ("Chịu Lỗi", "Skip, Restart", "restart_arrow", "Bỏ qua bản ghi hỏng và chạy tiếp khi gặp sự cố"),
    ]
    card_w = Inches(2.14)
    for i, (title, sub, icon_name, desc) in enumerate(summary_cards):
        cx = c_left + Inches(i * (2.14 + 0.15))
        cy = c_top + Inches(0.1)
        draw_card(s12, cx, cy, card_w, Inches(3.6), COLOR_WHITE)
        
        sb = s12.shapes.add_shape(MSO_SHAPE.RECTANGLE, cx, cy, card_w, Inches(0.55))
        sb.fill.solid()
        sb.fill.fore_color.rgb = COLOR_MINT
        sb.line.color.rgb = COLOR_NAVY
        sb.line.width = Pt(2)
        sp = sb.text_frame.paragraphs[0]
        sp.alignment = PP_ALIGN.CENTER
        sr = sp.add_run()
        sr.text = title
        sr.font.name = FONT_TITLE
        sr.font.size = Pt(20)
        sr.font.bold = True
        sr.font.color.rgb = COLOR_NAVY
        
        s12.shapes.add_picture(f"slides/assets/icons/{icon_name}.png", cx + Inches(0.62), cy + Inches(0.7), Inches(0.9), Inches(0.9))
        
        sbox = s12.shapes.add_textbox(cx + Inches(0.1), cy + Inches(1.75), card_w - Inches(0.2), Inches(1.7))
        stf = sbox.text_frame
        stf.word_wrap = True
        p1 = stf.paragraphs[0]
        p1.alignment = PP_ALIGN.CENTER
        p1.space_after = Pt(4)
        r1 = p1.add_run()
        r1.text = sub
        r1.font.name = FONT_BODY
        r1.font.size = Pt(16)
        r1.font.bold = True
        r1.font.color.rgb = COLOR_NAVY
        
        p2 = stf.add_paragraph()
        p2.alignment = PP_ALIGN.CENTER
        r2 = p2.add_run()
        r2.text = desc
        r2.font.name = FONT_BODY
        r2.font.size = Pt(14)
        r2.font.color.rgb = COLOR_NAVY
        
    # Level transition button banner
    draw_card(s12, c_left, c_top + Inches(3.9), Inches(11.3), Inches(1.05), COLOR_YELLOW)
    s12.shapes.add_picture("slides/assets/icons/play_button.png", c_left + Inches(0.3), c_top + Inches(4.05), Inches(0.75), Inches(0.75))
    
    tbox = s12.shapes.add_textbox(c_left + Inches(1.2), c_top + Inches(4.05), Inches(9.8), Inches(0.8))
    tp = tbox.text_frame.paragraphs[0]
    tr = tp.add_run()
    tr.text = "NGHỈ GIẢI LAO: TIẾP THEO LÀ PHẦN ÁP DỤNG TRONG ĐỒ ÁN VÀ DEMO"
    tr.font.name = FONT_TITLE
    tr.font.size = Pt(24)
    tr.font.bold = True
    tr.font.color.rgb = COLOR_NAVY
    
    add_notes(s12, "Để ghi nhớ Spring Batch, chúng ta chỉ cần 5 chìa khóa: Job và Step tổ chức luồng, Reader Processor Writer phân chia khâu xử lý, Chunk tối ưu bộ nhớ, JobRepository lưu tiến độ, cùng Skip và Restart bảo vệ trước sự cố. Bây giờ, chúng ta bước sang phần áp dụng thực tế vào đồ án kiến trúc microservices và kịch bản demo.")

    # =========================================================================
    # SLIDE 13 (Part C, Sky Blue)
    # =========================================================================
    s13 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s13)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s13, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Trong đồ án: Snapgram cũng có bài toán như ngân hàng",
        section="C"
    )
    draw_footer_and_progress(s13, 13, 16)
    
    # Left Card: Short Intro to Snapgram & Recommender Problem
    draw_card(s13, c_left, c_top, Inches(7.3), Inches(4.9), COLOR_WHITE)
    lbox = s13.shapes.add_textbox(c_left + Inches(0.3), c_top + Inches(0.2), Inches(6.7), Inches(4.5))
    ltf = lbox.text_frame
    ltf.word_wrap = True
    
    p = ltf.paragraphs[0]
    p.space_after = Pt(10)
    r = p.add_run()
    r.text = "Snapgram: mạng xã hội kiến trúc microservices"
    r.font.name = FONT_TITLE
    r.font.size = Pt(24)
    r.font.bold = True
    r.font.color.rgb = COLOR_NAVY
    
    snapgram_bullets = [
        "Muốn app hiển thị tức thì Bài viết thịnh hành và Gợi ý người theo dõi",
        "Service gợi ý (recommender-service) phải chấm điểm hàng loạt bài viết",
        "Nếu mỗi lần người dùng mở app mới đi quét database thì hệ thống nghẽn ngay",
        "Giải pháp chuẩn: Spring Batch tính điểm trước định kỳ rồi lưu sẵn vào kho",
        "Y hệt bài toán ngân hàng tính lãi trước ở Slide 2"
    ]
    for b in snapgram_bullets:
        bp = ltf.add_paragraph()
        bp.space_after = Pt(6)
        br = bp.add_run()
        br.text = f"* {b}"
        br.font.name = FONT_BODY
        br.font.size = Pt(15.5)
        br.font.color.rgb = COLOR_NAVY
    
    # Right Card: Phone Mockup illustration
    draw_card(s13, c_left + Inches(7.6), c_top, Inches(3.7), Inches(4.9), COLOR_CREAM)
    s13.shapes.add_picture("slides/assets/icons/phone.png", c_left + Inches(8.7), c_top + Inches(0.3), Inches(1.5), Inches(1.5))
    
    pbox = s13.shapes.add_textbox(c_left + Inches(7.8), c_top + Inches(2.0), Inches(3.3), Inches(2.7))
    ptf = pbox.text_frame
    ptf.word_wrap = True
    
    pp1 = ptf.paragraphs[0]
    pp1.alignment = PP_ALIGN.CENTER
    pp1.space_after = Pt(8)
    pr1 = pp1.add_run()
    pr1.text = "GIAO DIỆN SNAPGRAM"
    pr1.font.name = FONT_TITLE
    pr1.font.size = Pt(20)
    pr1.font.bold = True
    pr1.font.color.rgb = COLOR_NAVY
    
    phone_items = [
        "Mục 1: Bài viết thịnh hành (Top 50 Explore Feed)",
        "Mục 2: Gợi ý người theo dõi (Friend-of-Friends)",
        "Đọc siêu tốc từ cache Redis đã tính sẵn"
    ]
    for b in phone_items:
        bp = ptf.add_paragraph()
        bp.space_after = Pt(6)
        br = bp.add_run()
        br.text = f"* {b}"
        br.font.name = FONT_BODY
        br.font.size = Pt(14)
        br.font.color.rgb = COLOR_NAVY
    
    add_notes(s13, "Bước vào dự án Snapgram theo kiến trúc microservices. Người dùng mở app luôn muốn thấy ngay Bài viết thịnh hành và Gợi ý bạn bè. Nếu mỗi lần mở app mới đi quét toàn bộ dữ liệu thì hệ thống sẽ tắc nghẽn. Do đó, service gợi ý áp dụng đúng bài toán batch: tính toán trước hàng loạt rồi lưu sẵn vào bộ nhớ tạm. Giờ hãy xem chi tiết hai Job.")

    # =========================================================================
    # SLIDE 14 (Part C, Sky Blue)
    # =========================================================================
    s14 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s14)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s14, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Hai Job trong recommender-service chạy thế nào",
        section="C"
    )
    draw_footer_and_progress(s14, 14, 16)
    
    # Top Card: Architecture Data Flow (Kafka -> Batch -> DB/Redis -> App)
    draw_card(s14, c_left, c_top, Inches(11.3), Inches(1.15), COLOR_CREAM)
    afbox = s14.shapes.add_textbox(c_left + Inches(0.2), c_top + Inches(0.08), Inches(10.9), Inches(1.0))
    aftf = afbox.text_frame
    aftf.word_wrap = True
    ap = aftf.paragraphs[0]
    ar = ap.add_run()
    ar.text = "Luồng kiến trúc: Dữ liệu nhận qua Kafka (hộp thư chuyển tin giữa các service) để cập nhật bảng đọc riêng. Spring Batch định kỳ chấm điểm hàng loạt (chunk 20), lưu vào PostgreSQL và Redis (bộ nhớ tạm truy cập nhanh), app chỉ việc đọc từ Redis."
    ar.font.name = FONT_BODY
    ar.font.size = Pt(16)
    ar.font.color.rgb = COLOR_NAVY
    
    # 2 Job Cards side by side
    job_w = Inches(5.45)
    jtop = c_top + Inches(1.25)
    
    # Job 1 Card
    draw_card(s14, c_left, jtop, job_w, Inches(2.75), COLOR_WHITE)
    j1b = s14.shapes.add_textbox(c_left + Inches(0.2), jtop + Inches(0.12), job_w - Inches(0.4), Inches(2.55))
    j1tf = j1b.text_frame
    j1tf.word_wrap = True
    j1_bullets = [
        "Đọc bài viết công khai từ bảng recommender_post_view",
        "Chấm điểm theo lượt thích, thời gian xem và độ mới",
        "Lưu Top 50 bài hot nhất vào Redis key trending:posts",
        "Gán ID = postId đảm bảo an toàn khi chạy lại"
    ]
    add_bullet_list(j1tf, j1_bullets, font_size=Pt(14), space_after=Pt(3), title_text="trendingPostsJob (Bài viết thịnh hành):", title_size=Pt(21))
    
    # Job 2 Card
    draw_card(s14, c_left + Inches(5.85), jtop, job_w, Inches(2.75), COLOR_WHITE)
    j2b = s14.shapes.add_textbox(c_left + Inches(6.05), jtop + Inches(0.12), job_w - Inches(0.4), Inches(2.55))
    j2tf = j2b.text_frame
    j2tf.word_wrap = True
    j2_bullets = [
        "Đọc danh sách người dùng từ bảng quan hệ theo dõi",
        "Tính toán danh sách bạn chung (Friend-of-Friends)",
        "Lưu kết quả gợi ý vào PostgreSQL và Redis theo user",
        "Chạy định kỳ tự động qua cấu hình cron job"
    ]
    add_bullet_list(j2tf, j2_bullets, font_size=Pt(14), space_after=Pt(3), title_text="userRecommendationJob (Gợi ý theo dõi):", title_size=Pt(21))
    
    # Bottom Admin API Note Card
    draw_card(s14, c_left, c_top + Inches(4.15), Inches(11.3), Inches(0.85), COLOR_SKY)
    s14.shapes.add_picture("slides/assets/icons/lock.png", c_left + Inches(0.2), c_top + Inches(4.25), Inches(0.65), Inches(0.65))
    apibox = s14.shapes.add_textbox(c_left + Inches(1.0), c_top + Inches(4.18), Inches(10.1), Inches(0.75))
    apip = apibox.text_frame.paragraphs[0]
    apir = apip.add_run()
    apir.text = "API Quản trị (/api/admin/batch/**): yêu cầu quyền Admin, chống chạy trùng (trả 409 Conflict). Service gợi ý không đọc trực tiếp database của service khác."
    apir.font.name = FONT_BODY
    apir.font.size = Pt(16)
    apir.font.bold = True
    apir.font.color.rgb = COLOR_NAVY
    
    add_notes(s14, "Hệ thống vận hành với hai Job chính: trendingPostsJob chấm điểm bài viết hot lưu vào Redis cache, và userRecommendationJob tính toán gợi ý bạn chung. Dữ liệu được đồng bộ bất đồng bộ qua Kafka, đảm bảo service gợi ý không truy vấn trực tiếp vào database của các service khác. Toàn bộ API kích hoạt và giám sát đều yêu cầu quyền Admin và có cơ chế chống chạy trùng. Sau đây là kịch bản demo 5 bước.")

    # =========================================================================
    # SLIDE 15 (Part C, Sky Blue)
    # =========================================================================
    s15 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s15)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s15, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Demo: 5 bước trên bản đồ màn chơi",
        section="C"
    )
    draw_footer_and_progress(s15, 15, 16)
    
    steps_data = [
        ("MÀN 1", "seed_data", "Nạp 5.000 bản ghi", "Cài 3 bản ghi lỗi cố ý", "warning_triangle", "Minh họa Skip"),
        ("MÀN 2", "run_job", "Khởi chạy Job", "Xem log commit từng xấp 20", "conveyor", "Minh họa Chunk"),
        ("MÀN 3", "metrics", "Kiểm tra số liệu", "Xem read, write, skip, commit", "notebook", "Minh họa Metadata"),
        ("MÀN 4", "cache", "Người dùng đọc cache", "Lấy Top 50 siêu tốc từ Redis", "coin", "Minh họa Cache-First"),
        ("MÀN 5", "restart", "Phục hồi sự cố", "Lỗi item 45, restart chạy tiếp", "restart_arrow", "Minh họa Restart"),
    ]
    card_w = Inches(2.14)
    card_h = Inches(4.4)
    for i, (stage, code_name, title, desc, icon_name, concept) in enumerate(steps_data):
        cx = c_left + Inches(i * (2.14 + 0.15))
        cy = c_top + Inches(0.1)
        card_bg = COLOR_WHITE if i % 2 == 0 else COLOR_CREAM
        draw_card(s15, cx, cy, card_w, card_h, card_bg)
        
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
        
        s15.shapes.add_picture(f"slides/assets/icons/{icon_name}.png", cx + Inches(0.57), cy + Inches(0.65), Inches(1.0), Inches(1.0))
        
        sbox = s15.shapes.add_textbox(cx + Inches(0.1), cy + Inches(1.75), card_w - Inches(0.2), Inches(2.5))
        stf = sbox.text_frame
        stf.word_wrap = True
        
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
        
        p3 = stf.add_paragraph()
        p3.alignment = PP_ALIGN.CENTER
        r3 = p3.add_run()
        r3.text = f"[{concept}]"
        r3.font.name = FONT_TITLE
        r3.font.size = Pt(18)
        r3.font.bold = True
        r3.font.color.rgb = COLOR_CORAL
        
    add_notes(s15, "Để chứng minh thực tế, nhóm đã chuẩn bị kịch bản demo gồm 5 bước như 5 màn chơi. Từ bước nạp dữ liệu có cài sẵn bản ghi lỗi, kích hoạt job để thấy xử lý từng xấp, kiểm tra số liệu lưu trong sổ, đọc cache Redis, cho đến bước giả lập sự cố ở bản ghi 45 rồi restart thành công. Giờ chúng ta hãy xem bảng kết quả và các hướng tối ưu.")

    # =========================================================================
    # SLIDE 16 (Part C, Sky Blue)
    # =========================================================================
    s16 = prs.slides.add_slide(prs.slide_layouts[6])
    set_slide_background(s16)
    c_left, c_top, c_w, c_h = draw_retro_window(
        s16, Inches(0.6), Inches(0.4), Inches(12.133), Inches(6.45),
        "Kết quả demo và các hướng cải tiến tiếp theo",
        section="C"
    )
    draw_footer_and_progress(s16, 16, 16)
    
    # Left Card: Demo Metrics Table with BLANK values
    metric_card_w = Inches(5.6)
    draw_card(s16, c_left, c_top, metric_card_w, Inches(3.95), COLOR_WHITE)
    
    mtab = s16.shapes.add_shape(MSO_SHAPE.RECTANGLE, c_left, c_top, metric_card_w, Inches(0.55))
    mtab.fill.solid()
    mtab.fill.fore_color.rgb = COLOR_SKY
    mtab.line.color.rgb = COLOR_NAVY
    mtab.line.width = Pt(2)
    mp = mtab.text_frame.paragraphs[0]
    mr = mp.add_run()
    mr.text = "SỐ LIỆU CHẠY THỰC TẾ (DEMO)"
    mr.font.name = FONT_TITLE
    mr.font.size = Pt(20)
    mr.font.bold = True
    mr.font.color.rgb = COLOR_NAVY
    
    mbox = s16.shapes.add_textbox(c_left + Inches(0.25), c_top + Inches(0.65), metric_card_w - Inches(0.4), Inches(3.2))
    mtf = mbox.text_frame
    mtf.word_wrap = False
    
    metrics_rows = [
        ("Số bài đọc (Read):", "[           ]"),
        ("Số bản ghi ghi (Written):", "[           ]"),
        ("Số bản ghi bỏ qua (Skip):", "[           ]"),
        ("Số lần commit (Commits):", "[           ]"),
        ("Thời gian chạy (Duration):", "[           ]"),
    ]
    for idx, (label, blank_val) in enumerate(metrics_rows):
        p = mtf.paragraphs[0] if idx == 0 else mtf.add_paragraph()
        p.space_after = Pt(5)
        r1 = p.add_run()
        r1.text = f"{label:<28} "
        r1.font.name = FONT_BODY
        r1.font.size = Pt(15.5)
        r1.font.bold = True
        r1.font.color.rgb = COLOR_NAVY
        
        r2 = p.add_run()
        r2.text = blank_val
        r2.font.name = FONT_BODY
        r2.font.size = Pt(15.5)
        r2.font.color.rgb = COLOR_CORAL
        
    p_note = mtf.add_paragraph()
    p_note.space_before = Pt(4)
    rn = p_note.add_run()
    rn.text = "* Để trống để điền số liệu từ lần chạy thực tế."
    rn.font.name = FONT_BODY
    rn.font.size = Pt(13)
    rn.font.color.rgb = COLOR_NAVY
    
    # Right Card: 3 Future technical improvements
    right_card_w = Inches(5.4)
    draw_card(s16, c_left + Inches(5.9), c_top, right_card_w, Inches(3.95), COLOR_WHITE)
    
    rtab = s16.shapes.add_shape(MSO_SHAPE.RECTANGLE, c_left + Inches(5.9), c_top, right_card_w, Inches(0.55))
    rtab.fill.solid()
    rtab.fill.fore_color.rgb = COLOR_MINT
    rtab.line.color.rgb = COLOR_NAVY
    rtab.line.width = Pt(2)
    rp = rtab.text_frame.paragraphs[0]
    rr = rp.add_run()
    rr.text = "BA HƯỚNG TỐI ƯU HÓA KỸ THUẬT"
    rr.font.name = FONT_TITLE
    rr.font.size = Pt(20)
    rr.font.bold = True
    rr.font.color.rgb = COLOR_NAVY
    
    rbox = s16.shapes.add_textbox(c_left + Inches(6.1), c_top + Inches(0.65), right_card_w - Inches(0.4), Inches(3.2))
    rtf = rbox.text_frame
    rtf.word_wrap = True
    
    future_items = [
        "Tối ưu truy vấn: gom các câu hỏi phụ thành GROUP BY trong database để giảm số câu truy vấn riêng lẻ.",
        "Khóa phân tán: hiện tại kiểm tra chạy trùng trên 1 máy, khi scale nhiều máy cần dùng ShedLock.",
        "Xử lý song song (Partitioning): khi dữ liệu lên hàng triệu bản ghi, chia Step chạy song song đa luồng."
    ]
    add_bullet_list(rtf, future_items, font_size=Pt(15), space_after=Pt(6))
    
    # Bottom Q&A Button
    draw_card(s16, c_left, c_top + Inches(4.15), Inches(11.3), Inches(0.75), COLOR_YELLOW)
    s16.shapes.add_picture("slides/assets/icons/checkmark.png", c_left + Inches(3.8), c_top + Inches(4.25), Inches(0.55), Inches(0.55))
    qabox = s16.shapes.add_textbox(c_left + Inches(4.5), c_top + Inches(4.2), Inches(4.5), Inches(0.6))
    qp = qabox.text_frame.paragraphs[0]
    qr = qp.add_run()
    qr.text = "HỎI ĐÁP VÀ THẢO LUẬN"
    qr.font.name = FONT_TITLE
    qr.font.size = Pt(24)
    qr.font.bold = True
    qr.font.color.rgb = COLOR_NAVY
    
    add_notes(s16, "Bảng bên trái là các chỉ số từ JobRepository để điền số liệu thực tế khi demo. Về định hướng phát triển, nhóm đề xuất 3 giải pháp nâng cao: gom nhóm truy vấn cơ sở dữ liệu, dùng ShedLock khi scale nhiều máy, và áp dụng Step Partitioning khi dữ liệu lên hàng triệu dòng. Nhóm xin cảm ơn và kính mời thầy cô đặt câu hỏi.")

    prs.save("slides/snapgram-spring-batch.pptx")
    print(f"Generated all 16 slides in slides/snapgram-spring-batch.pptx!")

if __name__ == "__main__":
    build_all()
