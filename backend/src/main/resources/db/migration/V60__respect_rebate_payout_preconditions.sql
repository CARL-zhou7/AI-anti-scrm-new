-- Preserve rule sources and frozen session snapshots; clarify when a payout is due.
UPDATE scene_rule
SET rule_text=CONCAT(
  '前置条件核对：逐笔核对原始返款约定与实际完成情况。原约定完成操作或确认后返款，且该笔前置步骤尚未完成时，不能仅因受害者质疑未到账就触发立即出款；先回应具体步骤或其不接受该顺序的决定，不反复催促同一步骤。上一笔已到期未返的款项与本笔条件尚未满足的款项分别处理。',CHAR(10),
  '下文立即兑现的要求仅适用于原约定已满足、应付但尚未实际支付的款项，或原本明确承诺先付的款项；不得事后添加付款、解冻、追加任务等前提拖延已到期款项。',CHAR(10),
  rule_text),
  version_no=version_no+1, updated_at=CURRENT_TIMESTAMP(3)
WHERE scenario_code='TASK_REBATE' AND target_role='SCAMMER' AND title='小额返利实际兑现';
