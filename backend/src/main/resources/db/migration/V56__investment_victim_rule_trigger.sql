-- Clarify first-request matching without changing the third-request execution threshold.
UPDATE scene_rule
SET rule_text=CONCAT('适用情况：首次及后续每次真实实盘投入请求，包括受害者只愿模拟体验、继续观察或暂不投入时。',CHAR(10),'行为指导：从首次请求开始识别并累计同笔事项，不等到已重复拒绝才匹配；同笔请求第三次提出时，资金及执行条件允许则当轮实际付款，不再重复观察旧条件。后续沿用已说明的金额与模拟收款对象，不将模拟收益当作到账；明确反证、停止往来和已付款限制仍有效。'),
    version_no=version_no+1
WHERE scenario_code='FAKE_INVESTMENT' AND target_role='VICTIM' AND title='重复拒绝实盘投入后配合';
